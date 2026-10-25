package com.vknxworks.truectrl.data.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import java.util.UUID

/** The active server and its live connection. */
class ActiveServer(val server: Server, val connection: Connection)

/**
 * Keeps the list of servers, which one is active, a live connection to it and what it's allowed
 * to do. The connection is open only while the app is in the foreground ([setForeground]).
 * Switching servers, or changing a server's address, sign-in or certificate, replaces the
 * connection; settings like read-only mode don't.
 */
class Sessions(
    private val store: ServerStore,
    private val baseHttp: OkHttpClient,
    private val scope: CoroutineScope,
    lockedAtStart: Boolean,
) {
    /** While the app lock is locked, no connection is opened (the sign-in may be sealed anyway). */
    @Volatile
    private var locked = lockedAtStart

    val servers: StateFlow<List<Server>> = store.servers

    /** Bumped when secrets change (signing in again), so the connection is rebuilt. */
    private val revision = MutableStateFlow(0)

    @Volatile
    private var foreground = false
    private var cached: Pair<String, Connection>? = null

    val active: StateFlow<ActiveServer?> = combine(store.servers, store.activeId, revision) { list, id, rev ->
        val server = list.firstOrNull { it.id == id }
        if (server == null) {
            dropConnection()
            null
        } else {
            ActiveServer(server, connectionFor(server, rev))
        }
    }.stateIn(scope, SharingStarted.Eagerly, initialActive())

    val access: StateFlow<Access> = active
        .flatMapLatest { it?.connection?.access ?: flowOf(Access(loading = false)) }
        .stateIn(scope, SharingStarted.Eagerly, Access(loading = active.value != null))

    private fun initialActive(): ActiveServer? =
        store.servers.value.firstOrNull { it.id == store.activeId.value }?.let { ActiveServer(it, connectionFor(it, 0)) }

    /** Reuses the connection while nothing it depends on changed; otherwise closes it and makes a new one. */
    @Synchronized
    private fun connectionFor(server: Server, rev: Int): Connection {
        val key = listOf(server.id, server.url, server.httpAccepted.toString(), server.auth.name, server.username, server.certPin, server.usesOtp.toString(), rev.toString())
            .joinToString("|")
        cached?.let { (k, c) -> if (k == key) return c }
        cached?.second?.stop()
        val connection = Connection(
            server = server,
            baseHttp = baseHttp,
            secrets = { store.secrets(server.id) },
            scope = scope,
            // The live setting, not this copy: read-only mode changes without replacing the connection.
            readOnly = { store.servers.value.firstOrNull { it.id == server.id }?.readOnly ?: true },
            onVersion = { v -> if (v != server.tnVersion) store.update(server.id) { it.copy(tnVersion = v) } },
        )
        cached = key to connection
        if (foreground && !locked) connection.start()
        return connection
    }

    /**
     * A separate, short-lived connection for a background check of [server]. It never changes
     * anything (read-only whatever the setting says) and is closed by the caller when done.
     */
    fun backgroundConnection(server: Server): Connection = Connection(
        server = server,
        baseHttp = baseHttp,
        secrets = { store.secrets(server.id) },
        scope = scope,
        readOnly = { true },
    )

    @Synchronized
    private fun dropConnection() {
        cached?.second?.stop()
        cached = null
    }

    /** The app came to the foreground (open the connection) or went to the background (close it). */
    fun setForeground(value: Boolean) {
        foreground = value
        val connection = active.value?.connection ?: return
        if (value && !locked) connection.start() else if (!value) connection.stop()
    }

    /**
     * The app locked (close the connection and forget it) or unlocked (connect again with the
     * sign-in, which can be read again now). The server list stays, so no sign-in screen shows.
     */
    fun setLocked(value: Boolean) {
        if (locked == value) return
        locked = value
        if (value) active.value?.connection?.stop()
        revision.value++
    }

    /** Connects again now (the Retry button). */
    fun reconnect() {
        active.value?.connection?.restart()
    }

    /** True when the active server isn't in read-only mode and the user has every one of [roles]. */
    fun canWrite(vararg roles: String): Boolean {
        val server = active.value?.server ?: return false
        val caps = access.value.capabilities ?: return false
        return !server.readOnly && caps.hasAllRoles(*roles)
    }

    fun add(server: Server, secrets: ServerSecrets): Server {
        val saved = if (server.id.isBlank()) server.copy(id = UUID.randomUUID().toString()) else server
        store.saveSecrets(saved.id, secrets)
        store.save(saved)
        store.setActive(saved.id)
        // Editing a server can leave its settings unchanged but its secrets new: reconnect either way.
        revision.value++
        return saved
    }

    fun switchTo(id: String) = store.setActive(id)

    fun remove(id: String) = store.remove(id)

    fun setReadOnly(id: String, readOnly: Boolean) = store.update(id) { it.copy(readOnly = readOnly) }
}
