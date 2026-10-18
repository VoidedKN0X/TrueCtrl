package com.vknxworks.truectrl.data.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A change pushed by TrueNAS for a subscription (`collection_update`), or the end of one
 * (`notify_unsubscribed`, with [msg] = "unsubscribed").
 */
data class RpcEvent(
    val collection: String,
    /** "added", "changed", "removed" or "unsubscribed". */
    val msg: String,
    val id: JsonElement?,
    val fields: JsonObject?,
)

/**
 * One JSON-RPC 2.0 WebSocket connection to TrueNAS (`wss://<host>/api/current`). Calls can run
 * concurrently; each waits for the response with its id. Subscription updates arrive on [events].
 * Nothing is logged: requests carry API keys and passwords.
 */
class RpcSocket(private val http: OkHttpClient, private val url: String) {
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val opened = CompletableDeferred<Unit>()
    private val ended = CompletableDeferred<TnError>()

    private class Pending(val method: String, val result: CompletableDeferred<JsonElement>)

    @Volatile
    private var ws: WebSocket? = null

    private val _events = MutableSharedFlow<RpcEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<RpcEvent> = _events.asSharedFlow()

    /** Completes with the reason when the connection ends, for whatever reason. */
    val closed: Deferred<TnError> get() = ended

    val isOpen: Boolean get() = opened.isCompleted && !ended.isCompleted

    /** Opens the connection and waits until it's ready (or fails with a [TnError]). */
    suspend fun open(timeoutMs: Long = 15_000) {
        ws = http.newWebSocket(Request.Builder().url(url).build(), listener)
        try {
            withTimeout(timeoutMs) { opened.await() }
        } catch (e: TimeoutCancellationException) {
            fail(TnError.Unreachable(e))
            throw TnError.Unreachable(e)
        }
    }

    /** Calls [method] with positional [params] and returns its `result`. */
    suspend fun call(method: String, params: List<JsonElement> = emptyList(), timeoutMs: Long = 60_000): JsonElement {
        if (ended.isCompleted) throw TnError.Unreachable(null)
        val id = nextId.getAndIncrement().toString()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = Pending(method, deferred)
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", JsonArray(params))
        }
        val sent = ws?.send(message.toString()) ?: false
        if (!sent) {
            pending.remove(id)
            throw TnError.Unreachable(null)
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw TnError.Unreachable(e)
        } finally {
            pending.remove(id)
        }
    }

    /** Closes the connection; calls still waiting fail. */
    fun close() {
        ws?.close(1000, null)
        fail(TnError.Unreachable(null))
    }

    private fun fail(error: TnError) {
        opened.completeExceptionally(error)
        ended.complete(error)
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it.result.completeExceptionally(error) }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handle(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            fail(TnError.Unreachable(null))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val code = response?.code ?: 0
            val error = when {
                response == null -> TnError.fromIo(t)
                // The address answered over HTTPS but has no TrueNAS API there.
                code == 404 -> TnError.NotTrueNas(null)
                // A reverse proxy answers 502/503/504 while TrueNAS itself is down or rebooting.
                code in 500..599 -> TnError.Unreachable(t)
                else -> TnError.NotTrueNas("HTTP $code")
            }
            response?.close()
            fail(error)
        }
    }

    private fun handle(text: String) {
        val root = runCatching { TnJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
        val id = root["id"].str()
        if (id != null) {
            val call = pending.remove(id) ?: return
            val error = root["error"].obj()
            if (error != null) {
                call.result.completeExceptionally(errorFrom(error, call.method))
            } else {
                call.result.complete(root["result"] ?: JsonNull)
            }
            return
        }
        val params = root["params"].obj() ?: return
        when (root["method"].str()) {
            "collection_update" -> _events.tryEmit(
                RpcEvent(
                    collection = params["collection"].str().orEmpty(),
                    msg = params["msg"].str().orEmpty(),
                    id = params["id"],
                    fields = params["fields"].obj(),
                ),
            )
            "notify_unsubscribed" -> _events.tryEmit(
                RpcEvent(collection = params["collection"].str().orEmpty(), msg = "unsubscribed", id = null, fields = null),
            )
        }
    }

    companion object {
        private const val METHOD_NOT_FOUND = -32601L

        /**
         * Turns a JSON-RPC error into a [TnError]. TrueNAS puts its own error in `data`:
         * `{error: errno, errname: "EACCES", reason: "...", extra: [...]}`.
         */
        fun errorFrom(error: JsonObject, method: String): TnError {
            val code = error["code"].long()
            val data = error["data"].obj()
            val errname = data?.get("errname").str()
            val reason = data?.get("reason").str()?.trim()?.takeIf { it.isNotBlank() } ?: error["message"].str()
            return when {
                code == METHOD_NOT_FOUND -> TnError.MethodMissing(method)
                errname == "EACCES" || errname == "EPERM" -> TnError.Forbidden(reason)
                errname == "ENOTAUTHENTICATED" -> TnError.NotAuthenticated()
                else -> TnError.Call(errname, reason)
            }
        }
    }
}
