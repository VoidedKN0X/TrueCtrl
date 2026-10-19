package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.server.Connection
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * The TrueNAS web shell (`wss://<host>/websocket/shell`), opened by the app so it can use the
 * pinned certificate. It signs in with a short-lived token from auth.generate_token, sent as the
 * first message; TrueNAS answers `{"msg": "connected", "id": …}` and from then on carries the
 * terminal's bytes both ways. The size is set with core.resize_shell on the API connection.
 */
class ShellSession(private val connection: Connection) {

    interface Listener {
        fun onConnected()
        fun onBytes(bytes: ByteArray)
        fun onClosed(code: Int, reason: String)
        fun onFailure(error: Throwable)
    }

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var shellId: String? = null

    /** Gets a token and connects. API errors are thrown; socket events go to [listener]. */
    suspend fun connect(listener: Listener) {
        // One minute is plenty to open the socket; the token isn't kept anywhere.
        val token = connection.call(
            "auth.generate_token",
            listOf(JsonPrimitive(60), JsonObject(emptyMap()), JsonPrimitive(true)),
        ).str() ?: throw TnError.Call(null, null)
        socket = connection.openWebSocket(
            "/websocket/shell",
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val hello = buildJsonObject {
                        put("token", token)
                        putJsonObject("options") { }
                    }
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (shellId == null) {
                        val msg: JsonElement? = runCatching { TnJson.parseToJsonElement(text) }.getOrNull()
                        when (msg["msg"].str()) {
                            "connected" -> {
                                shellId = msg["id"].str() ?: ""
                                listener.onConnected()
                            }
                            "failed" -> {
                                val reason = msg["error"]["reason"].str() ?: msg["error"].str()
                                listener.onFailure(if (reason?.contains("web_shell", true) == true) TnError.Forbidden(reason) else TnError.Call(null, reason))
                                webSocket.close(1000, null)
                            }
                        }
                    } else {
                        listener.onBytes(text.toByteArray())
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    listener.onBytes(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(code, reason)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    listener.onFailure(TnError.fromIo(t))
            },
        )
    }

    /** Typing goes as binary frames: TrueNAS writes them straight to the terminal. */
    fun send(bytes: ByteArray) {
        if (shellId != null) socket?.send(bytes.toByteString())
    }

    suspend fun resize(cols: Int, rows: Int) {
        val id = shellId?.takeIf { it.isNotEmpty() } ?: return
        runCatching { connection.call("core.resize_shell", listOf(JsonPrimitive(id), JsonPrimitive(cols), JsonPrimitive(rows))) }
    }

    fun close() {
        socket?.close(1000, null)
        socket = null
    }
}
