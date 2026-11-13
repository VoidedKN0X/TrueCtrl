package com.vknxworks.truectrl.data.overview

import android.content.Context
import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.UpdateApi
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.server.ActiveServer
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.server.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

data class OverviewState(
    val serverId: String? = null,
    val snapshot: OverviewSnapshot = OverviewSnapshot(),
    /** When [snapshot] was last read from the server (ms); 0 = never. */
    val updatedAt: Long = 0,
    /** The last refresh failed; [snapshot] is the last known state. */
    val error: TnError? = null,
    val loading: Boolean = false,
)

/** Live numbers from reporting.realtime, newest last; only kept while the Overview is on screen. */
data class Realtime(
    val cpu: List<Double?> = emptyList(),
    val memUsed: List<Double?> = emptyList(),
    val netIn: List<Double?> = emptyList(),
    val netOut: List<Double?> = emptyList(),
    val memTotal: Long = 0,
    val arcSize: Long? = null,
    /** ARC data demand hit percentage, 0–100. */
    val arcHit: Double? = null,
    val cpuTemp: Double? = null,
) {
    val hasData: Boolean get() = cpu.isNotEmpty()
}

@Serializable
private data class OverviewCache(val updatedAt: Long, val snapshot: OverviewSnapshot)

/**
 * The Overview's data: polled while the Overview is visible, alerts live through the alert.list
 * event, and CPU, memory, ARC and network live through reporting.realtime. Methods this user
 * can't call, or this TrueNAS version doesn't have, are skipped.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OverviewRepository(
    private val context: Context,
    private val sessions: Sessions,
    private val scope: CoroutineScope,
    /** Called (on an IO thread) with each newly read snapshot, for the widgets and tile. */
    private val onSnapshot: (com.vknxworks.truectrl.data.server.Server, OverviewSnapshot) -> Unit = { _, _ -> },
) {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    private val _realtime = MutableStateFlow(Realtime())
    val realtime: StateFlow<Realtime> = _realtime.asStateFlow()

    private val lock = Mutex()
    /** The legacy update check (25.04) asks the update server, so it runs at most this often. */
    private var legacyUpdateCheckedAt = 0L

    init {
        scope.launch { sessions.active.collect { switchTo(it) } }
        // Alerts change on their own: re-read the list whenever TrueNAS says it changed.
        scope.launch {
            sessions.active
                .flatMapLatest { it?.connection?.subscribe("alert.list") ?: emptyFlow() }
                .debounce(500)
                .collect { refreshAlerts() }
        }
    }

    private suspend fun switchTo(active: ActiveServer?) = lock.withLock {
        _realtime.value = Realtime()
        legacyUpdateCheckedAt = 0
        if (active == null) {
            _state.value = OverviewState()
            return@withLock
        }
        if (_state.value.serverId == active.server.id) return@withLock
        val cached = readCache(active.server.id)
        _state.value = OverviewState(
            serverId = active.server.id,
            snapshot = cached?.snapshot ?: OverviewSnapshot(),
            updatedAt = cached?.updatedAt ?: 0,
        )
    }

    /** Reads everything now. Errors are kept in the state; the last known Overview stays visible. */
    suspend fun refresh(force: Boolean = false) {
        val active = sessions.active.value ?: return
        lock.withLock {
            if (_state.value.serverId != active.server.id) return
            val caps = sessions.access.value.capabilities
            if (caps == null) {
                // Not signed in yet: the connection banner explains why; keep the cached view.
                _state.value = _state.value.copy(error = sessions.access.value.error, loading = false)
                return
            }
            _state.value = _state.value.copy(loading = true)
            try {
                val snapshot = read(active.connection, caps, force)
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(snapshot = snapshot, updatedAt = now, error = null, loading = false)
                writeCache(active.server.id, OverviewCache(now, snapshot))
                withContext(Dispatchers.IO) { runCatching { onSnapshot(active.server, snapshot) } }
            } catch (e: TnError) {
                _state.value = _state.value.copy(error = e, loading = false)
            }
        }
    }

    private suspend fun read(c: Connection, caps: Capabilities, force: Boolean): OverviewSnapshot {
        val previous = _state.value.snapshot
        val now = System.currentTimeMillis()
        // 25.04's update check asks the update server: once per connection, then every 6 hours.
        val checkLegacy = force || previous.update == null || now - legacyUpdateCheckedAt >= LEGACY_UPDATE_EVERY_MS
        if (checkLegacy) legacyUpdateCheckedAt = now
        return OverviewReader.read(c, caps, previous, checkLegacyUpdate = checkLegacy)
    }

    private suspend fun refreshAlerts() {
        val active = sessions.active.value ?: return
        if (_state.value.serverId != active.server.id) return
        if (sessions.access.value.capabilities?.has("alert.list") != true) return
        val list = runCatching { active.connection.call("alert.list").arr()?.mapNotNull(AlertInfo::from) }.getOrNull() ?: return
        lock.withLock {
            if (_state.value.serverId != active.server.id) return
            val snapshot = _state.value.snapshot.copy(alerts = list)
            _state.value = _state.value.copy(snapshot = snapshot)
            writeCache(active.server.id, OverviewCache(_state.value.updatedAt, snapshot))
            withContext(Dispatchers.IO) { runCatching { onSnapshot(active.server, snapshot) } }
        }
    }

    /** Dismisses ([dismiss] = true) or restores an alert, then re-reads the list. */
    suspend fun setDismissed(uuid: String, dismiss: Boolean) {
        val active = sessions.active.value ?: return
        active.connection.call(if (dismiss) "alert.dismiss" else "alert.restore", listOf(JsonPrimitive(uuid)))
        refreshAlerts()
    }

    /**
     * Follows reporting.realtime while collected (the Overview is on screen), keeping the last
     * [HISTORY] samples (2 minutes at one sample every 2 seconds).
     */
    suspend fun followRealtime() {
        val active = sessions.active.value ?: return
        active.connection.subscribe(REALTIME_EVENT).collect { e ->
            val f = e.fields ?: return@collect
            val r = _realtime.value
            val cpu = f["cpu"]["cpu"]["usage"].double()
            val mem = f["memory"]
            val total = mem["physical_memory_total"].long() ?: r.memTotal
            val available = mem["physical_memory_available"].long()
            val used = if (total > 0 && available != null) (total - available).toDouble() else null
            val ifaces = f["interfaces"] as? JsonObject
            val netIn = ifaces?.values?.sumOf { it["received_bytes_rate"].double() ?: 0.0 }
            val netOut = ifaces?.values?.sumOf { it["sent_bytes_rate"].double() ?: 0.0 }
            _realtime.value = r.copy(
                cpu = (r.cpu + cpu).takeLast(HISTORY),
                memUsed = (r.memUsed + used).takeLast(HISTORY),
                netIn = (r.netIn + netIn).takeLast(HISTORY),
                netOut = (r.netOut + netOut).takeLast(HISTORY),
                memTotal = total,
                arcSize = mem["arc_size"].long() ?: r.arcSize,
                arcHit = f["zfs"]["demand_data_hit_percentage"].double() ?: r.arcHit,
                cpuTemp = f["cpu"]["cpu"]["temp"].double()?.takeIf { it > 0 } ?: r.cpuTemp,
            )
        }
    }

    fun forget(serverId: String) {
        runCatching { cacheFile(serverId).delete() }
    }

    private fun cacheFile(serverId: String) = File(File(context.noBackupFilesDir, "cache").apply { mkdirs() }, "overview-$serverId.json")

    private suspend fun readCache(serverId: String): OverviewCache? = withContext(Dispatchers.IO) {
        runCatching { TnJson.decodeFromString(OverviewCache.serializer(), cacheFile(serverId).readText()) }.getOrNull()
    }

    private suspend fun writeCache(serverId: String, cache: OverviewCache) = withContext(Dispatchers.IO) {
        runCatching { cacheFile(serverId).writeText(TnJson.encodeToString(OverviewCache.serializer(), cache)) }
    }

    companion object {
        const val HISTORY = 60
        private const val REALTIME_EVENT = "reporting.realtime:{\"interval\":2}"
        private const val LEGACY_UPDATE_EVERY_MS = 6 * 60 * 60 * 1000L
    }
}
