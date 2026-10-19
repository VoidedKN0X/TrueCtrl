package com.vknxworks.truectrl.data.server

import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.LoginResult
import com.vknxworks.truectrl.data.api.ReadOnlyGate
import com.vknxworks.truectrl.data.api.RpcEvent
import com.vknxworks.truectrl.data.api.RpcSocket
import com.vknxworks.truectrl.data.overview.SystemInfo
import com.vknxworks.truectrl.data.api.TnAuth
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.httpFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.vknxworks.truectrl.data.api.str
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient

/** What the signed-in user may do, and the server's state, for the active server. */
data class Access(
    val capabilities: Capabilities? = null,
    val info: SystemInfo? = null,
    /** A signed-in connection is open right now. */
    val connected: Boolean = false,
    /** Connecting or signing in. */
    val loading: Boolean = true,
    /** Why the last attempt failed; cleared when connected. */
    val error: TnError? = null,
    /** Goes up with every new signed-in socket, so subscriptions know to subscribe again. */
    val generation: Int = 0,
)

/**
 * The live connection to one server: a single WebSocket, signed in, kept open while the app is in
 * the foreground ([start]) and closed in the background ([stop]). Dropped connections are retried
 * with backoff (1 s up to 1 min); errors only the user can fix stop the retries.
 */
class Connection(
    val server: Server,
    baseHttp: OkHttpClient,
    private val secrets: () -> ServerSecrets,
    private val scope: CoroutineScope,
    /**
     * Whether read-only mode is on for this server right now. Read on every call (the setting can
     * change while the connection stays open); true when in doubt.
     */
    private val readOnly: () -> Boolean,
    /** Called with the exact TrueNAS version after each sign-in, so it can be remembered. */
    private val onVersion: (String) -> Unit = {},
) {
    private val http = httpFor(baseHttp, server.host, server.certPin)

    private val _access = MutableStateFlow(Access())
    val access: StateFlow<Access> = _access.asStateFlow()

    private val _events = MutableSharedFlow<RpcEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Subscription updates from whichever socket is open. */
    val events: SharedFlow<RpcEvent> = _events.asSharedFlow()

    @Volatile
    private var socket: RpcSocket? = null
    private var job: Job? = null

    /** Opens the connection (if it isn't already) and keeps it open. */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** Closes the connection; the last known [access] is kept, marked not connected. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        socket?.close()
        socket = null
        _access.value = _access.value.copy(connected = false, loading = false)
    }

    /** Drops the current attempt and connects again right away (the Retry button). */
    fun restart() {
        stop()
        start()
    }

    private suspend fun run() {
        var backoff = MIN_BACKOFF_MS
        while (true) {
            _access.value = _access.value.copy(loading = true, connected = false)
            try {
                val s = open()
                backoff = MIN_BACKOFF_MS
                val reason = s.closed.await()
                socket = null
                _access.value = _access.value.copy(connected = false, loading = false, error = reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = TnError.fromIo(e)
                _access.value = _access.value.copy(connected = false, loading = false, error = error)
                if (error.needsUser) return
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Checks the server, opens the socket, signs in and reads what the user may do. */
    private suspend fun open(): RpcSocket {
        val newest = TnAuth.requireSupported(TnAuth.probe(http, server.url))
        val s = RpcSocket(http, TnAuth.socketUrl(server.url))
        try {
            s.open()
            val sec = secrets()
            val result = when (server.auth) {
                AuthMethod.API_KEY -> TnAuth.loginApiKey(s, server.username, sec.apiKey)
                AuthMethod.PASSWORD -> {
                    if (server.usesOtp || sec.password.isEmpty()) throw TnError.NeedsSignIn()
                    TnAuth.loginPassword(s, server.username, sec.password)
                }
            }
            if (result == LoginResult.OtpRequired) throw TnError.NeedsSignIn()
            val caps = TnAuth.capabilities(s, newest)
            val info = TnAuth.systemInfo(s)
            caps.version?.label?.let(onVersion)
            socket = s
            scope.launch {
                // Forward this socket's subscription updates until it closes.
                val forward = launch { s.events.collect { _events.emit(it) } }
                s.closed.await()
                forward.cancel()
            }
            _access.value = Access(
                capabilities = caps, info = info, connected = true, loading = false, error = null,
                generation = _access.value.generation + 1,
            )
            return s
        } catch (e: Throwable) {
            s.close()
            throw e
        }
    }

    /**
     * Calls [method] on the open connection, waiting up to [waitMs] for it to connect first.
     * Throws a [TnError] when it isn't connected in time or TrueNAS returns an error.
     * In read-only mode, anything that isn't a known read ([ReadOnlyGate]) is refused with
     * [TnError.ReadOnly] before it's sent: this is the one place every server call goes through.
     */
    suspend fun call(method: String, params: List<JsonElement> = emptyList(), waitMs: Long = 15_000): JsonElement {
        if (readOnly() && !ReadOnlyGate.allows(method)) throw TnError.ReadOnly(method)
        val s = socket?.takeIf { it.isOpen } ?: run {
            start()
            try {
                withTimeout(waitMs) { access.first { it.connected || (it.error?.needsUser == true) } }
            } catch (e: TimeoutCancellationException) {
                throw TnError.Unreachable(e)
            }
            socket?.takeIf { it.isOpen } ?: throw (access.value.error ?: TnError.Unreachable(null))
        }
        return s.call(method, params)
    }

    /**
     * Live updates for [event] (e.g. `alert.list` or `reporting.realtime:{"interval":2}`) while the
     * flow is collected. Subscribes again after every reconnect and unsubscribes when collection
     * stops. Nothing is emitted while disconnected; an event this user may not see ends quietly.
     */
    fun subscribe(event: String): Flow<RpcEvent> {
        val shortName = event.substringBefore(':')
        return access.map { if (it.connected) it.generation else -1 }.distinctUntilChanged().flatMapLatest { generation ->
            if (generation < 0) {
                emptyFlow()
            } else {
                channelFlow {
                    // Listen first, so nothing sent right after subscribing is missed.
                    val forward = launch {
                        events.collect { e -> if (e.collection.substringBefore(':') == shortName) send(e) }
                    }
                    val id = runCatching { call("core.subscribe", listOf(JsonPrimitive(event))).str() }.getOrNull()
                    if (id == null) {
                        forward.cancel()
                        return@channelFlow
                    }
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            runCatching { socket?.takeIf { it.isOpen }?.call("core.unsubscribe", listOf(JsonPrimitive(id))) }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
