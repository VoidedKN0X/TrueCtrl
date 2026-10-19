package com.vknxworks.truectrl.data.storage

import android.content.Context
import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.ScanInfo
import com.vknxworks.truectrl.data.overview.ScrubSchedule
import com.vknxworks.truectrl.data.server.ActiveServer
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.server.Jobs
import com.vknxworks.truectrl.data.server.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

data class StorageState(
    val serverId: String? = null,
    val snapshot: StorageSnapshot = StorageSnapshot(),
    val updatedAt: Long = 0,
    val error: TnError? = null,
    val loading: Boolean = false,
)

enum class ScrubAction { START, PAUSE, STOP }

enum class GraphRange(val unit: String) { HOUR("HOUR"), DAY("DAY"), WEEK("WEEK"), MONTH("MONTH") }

/** One graph from TrueNAS reporting: timestamps (s) and a series per legend entry. */
data class Graph(val times: List<Long>, val series: Map<String, List<Double?>>)

/** Temperature over the last week: lowest, average and highest. */
data class TempStats(val min: Double?, val avg: Double?, val max: Double?)

@Serializable
private data class StorageCache(val updatedAt: Long, val snapshot: StorageSnapshot)

/**
 * Pools (with their layout) and disks (with temperatures, and SMART on TrueNAS 25.04). Scrub
 * progress is live through the scan event while a Storage screen is visible.
 */
class StorageRepository(
    private val context: Context,
    private val sessions: Sessions,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = _state.asStateFlow()
    private val lock = Mutex()

    init {
        scope.launch { sessions.active.collect { switchTo(it) } }
    }

    private suspend fun switchTo(active: ActiveServer?) = lock.withLock {
        if (active == null) {
            _state.value = StorageState()
            return@withLock
        }
        if (_state.value.serverId == active.server.id) return@withLock
        val cached = readCache(active.server.id)
        _state.value = StorageState(active.server.id, cached?.snapshot ?: StorageSnapshot(), cached?.updatedAt ?: 0)
    }

    suspend fun refresh() {
        val active = sessions.active.value ?: return
        lock.withLock {
            if (_state.value.serverId != active.server.id) return
            val caps = sessions.access.value.capabilities
            if (caps == null) {
                _state.value = _state.value.copy(error = sessions.access.value.error, loading = false)
                return
            }
            _state.value = _state.value.copy(loading = true)
            try {
                val snapshot = read(active.connection, caps)
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(snapshot = snapshot, updatedAt = now, error = null, loading = false)
                writeCache(active.server.id, StorageCache(now, snapshot))
            } catch (e: TnError) {
                _state.value = _state.value.copy(error = e, loading = false)
            }
        }
    }

    private suspend fun read(c: Connection, caps: Capabilities): StorageSnapshot = coroutineScope {
        fun opt(method: String, vararg params: JsonElement) = async {
            if (!caps.has(method)) return@async null
            try {
                c.call(method, params.toList())
            } catch (e: TnError.Forbidden) {
                null
            } catch (e: TnError.MethodMissing) {
                null
            } catch (e: TnError.Call) {
                null
            }
        }
        val pools = opt("pool.query")
        val disks = opt("disk.query", JsonArray(emptyList()), buildJsonObject { putJsonObject("extra") { put("pools", true) } })
        val temps = opt("disk.temperatures")
        val scrubs = opt("pool.scrub.query")
        val smart = if (caps.hasSmartApi) opt("smart.test.results") else null

        val poolList = pools.await().arr()?.mapNotNull(PoolDetail::from).orEmpty()
        // The root dataset's compression ratio for each pool.
        val ratios: Map<String, String> = if (poolList.isNotEmpty() && caps.has("pool.dataset.query")) {
            runCatching {
                val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("id"), JsonPrimitive("in"), JsonArray(poolList.map { JsonPrimitive(it.info.name) })))))
                val options = buildJsonObject {
                    putJsonObject("extra") {
                        put("retrieve_children", false)
                        putJsonArray("properties") { add(JsonPrimitive("compressratio")) }
                    }
                }
                c.call("pool.dataset.query", listOf(filter, options)).arr()?.mapNotNull { d ->
                    val name = d["id"].str() ?: return@mapNotNull null
                    val ratio = d["compressratio"]["value"].str() ?: d["compressratio"]["parsed"].double()?.let { "%.2fx".format(it) }
                    ratio?.let { name to it }
                }?.toMap()
            }.getOrNull().orEmpty()
        } else {
            emptyMap()
        }

        val tempMap = temps.await().obj()?.mapNotNull { (k, v) -> tempOf(v)?.let { k to it } }?.toMap().orEmpty()
        val smartMap = smart?.await().arr()?.mapNotNull { e ->
            val disk = e["disk"].str() ?: return@mapNotNull null
            disk to SmartInfo(
                tests = e["tests"].arr()?.mapNotNull(SmartTest::from).orEmpty(),
                runningProgress = e["current_test"]["progress"].long()?.toInt(),
            )
        }?.toMap().orEmpty()

        StorageSnapshot(
            pools = poolList.map { p -> p.copy(compressRatio = ratios[p.info.name]) },
            disks = disks.await().arr()?.mapNotNull(DiskInfo::from).orEmpty().sortedWith(compareBy({ it.name.length }, { it.name })),
            temps = tempMap,
            scrubs = scrubs.await().arr()?.mapNotNull(ScrubSchedule::from).orEmpty(),
            smart = smartMap,
        )
    }

    /**
     * Follows scrub and resilver progress while collected. The event is `zfs.pool.scan` before
     * TrueNAS 26 and `pool.scan` from 26 on.
     */
    suspend fun followScans() {
        val active = sessions.active.value ?: return
        val caps = sessions.access.value.capabilities ?: return
        active.connection.subscribe(caps.scanEvent).collect { e ->
            val f = e.fields ?: return@collect
            val name = f["name"].str() ?: return@collect
            val scan = ScanInfo.from(f["scan"]) ?: return@collect
            val s = _state.value
            if (s.serverId != active.server.id) return@collect
            _state.value = s.copy(
                snapshot = s.snapshot.copy(
                    pools = s.snapshot.pools.map { p -> if (p.info.name == name) p.copy(info = p.info.copy(scan = scan)) else p },
                ),
            )
        }
    }

    /** Starts, pauses or stops a scrub; throws when TrueNAS refuses. */
    suspend fun scrub(poolId: Int, action: ScrubAction) {
        val active = sessions.active.value ?: return
        val job = active.connection.call("pool.scrub", listOf(JsonPrimitive(poolId), JsonPrimitive(action.name)))
        Jobs.awaitStart(active.connection, job)
        refresh()
    }

    /** Starts a SMART test on TrueNAS 25.04 (SHORT or LONG); throws when TrueNAS refuses. */
    suspend fun runSmartTest(disk: DiskInfo, type: String) {
        val active = sessions.active.value ?: return
        val args = JsonArray(listOf(buildJsonObject { put("identifier", disk.identifier); put("type", type) }))
        val result = active.connection.call("smart.test.manual_test", listOf(args))
        result.arr()?.firstOrNull()?.get("error").str()?.takeIf { it.isNotBlank() }?.let { throw TnError.Call(null, it) }
        refresh()
    }

    /** SMART attributes for one disk (TrueNAS 25.04, Full Admin). */
    suspend fun smartAttributes(disk: String): List<SmartAttribute> {
        val active = sessions.active.value ?: return emptyList()
        return SmartAttribute.list(active.connection.call("disk.smart_attributes", listOf(JsonPrimitive(disk))))
    }

    /** Temperature over the last [days] days. */
    suspend fun tempStats(disk: String, days: Int = 7): TempStats? {
        val active = sessions.active.value ?: return null
        val caps = sessions.access.value.capabilities ?: return null
        if (!caps.has("disk.temperature_agg")) return null
        val r = runCatching {
            active.connection.call("disk.temperature_agg", listOf(JsonArray(listOf(JsonPrimitive(disk))), JsonPrimitive(days)))
        }.getOrNull() ?: return null
        val e = r[disk] ?: return null
        return TempStats(e["min"].double(), e["avg"].double(), e["max"].double()).takeIf { it.min != null || it.avg != null || it.max != null }
    }

    /**
     * A disk's I/O ("disk", KiB/s read and written) or temperature ("disktemp", °C) graph.
     * TrueNAS names disk graphs "sda | Type: … | Model: … | Serial: …", so the identifier is looked
     * up first. Null when this disk has no such graph.
     */
    suspend fun diskGraph(graph: String, disk: String, range: GraphRange): Graph? {
        val active = sessions.active.value ?: return null
        val caps = sessions.access.value.capabilities ?: return null
        val c = active.connection
        val listMethod = listOf("reporting.netdata_graphs", "reporting.graphs").firstOrNull(caps::has) ?: return null
        val dataMethod = listOf("reporting.netdata_get_data", "reporting.get_data").firstOrNull(caps::has) ?: return null
        val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("="), JsonPrimitive(graph)))))
        val identifiers = runCatching { c.call(listMethod, listOf(filter)) }.getOrNull()
            .arr()?.firstOrNull()?.get("identifiers").arr()?.mapNotNull { it.str() }.orEmpty()
        val identifier = identifiers.firstOrNull { it == disk || it.substringBefore(" |").trim() == disk } ?: return null
        val graphs = JsonArray(listOf(buildJsonObject { put("name", graph); put("identifier", identifier) }))
        val query = buildJsonObject {
            put("unit", range.unit)
            put("page", 1)
            put("aggregate", false)
        }
        val r = runCatching { c.call(dataMethod, listOf(graphs, query)) }.getOrNull().arr()?.firstOrNull() ?: return null
        val legend = r["legend"].arr()?.mapNotNull { it.str() }.orEmpty()
        val rows = r["data"].arr().orEmpty()
        if (legend.size < 2 || rows.isEmpty()) return null
        val times = rows.map { row -> row.arr()?.firstOrNull().long() ?: 0L }
        val series = legend.drop(1).mapIndexed { i, name -> name to rows.map { row -> row.arr()?.getOrNull(i + 1).double() } }.toMap()
        return Graph(times, series)
    }

    fun forget(serverId: String) {
        runCatching { cacheFile(serverId).delete() }
    }

    private fun cacheFile(serverId: String) = File(File(context.noBackupFilesDir, "cache").apply { mkdirs() }, "storage-$serverId.json")

    private suspend fun readCache(serverId: String): StorageCache? = withContext(Dispatchers.IO) {
        runCatching { TnJson.decodeFromString(StorageCache.serializer(), cacheFile(serverId).readText()) }.getOrNull()
    }

    private suspend fun writeCache(serverId: String, cache: StorageCache) = withContext(Dispatchers.IO) {
        runCatching { cacheFile(serverId).writeText(TnJson.encodeToString(StorageCache.serializer(), cache)) }
    }
}
