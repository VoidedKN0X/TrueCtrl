package com.vknxworks.truectrl.data.protection

import android.content.Context
import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.str
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

data class ProtectionState(
    val serverId: String? = null,
    val snapshot: ProtectionSnapshot = ProtectionSnapshot(),
    val updatedAt: Long = 0,
    val error: TnError? = null,
    val loading: Boolean = false,
)

@Serializable
private data class ProtectionCache(val updatedAt: Long, val snapshot: ProtectionSnapshot)

/** Key for remembering a task's last success. */
fun successKey(kind: String, id: Int) = "$kind:$id"

/**
 * Periodic snapshot, replication, cloud sync, rsync and scrub tasks, and the snapshots themselves.
 * Snapshot methods are `zfs.snapshot.*` on TrueNAS 25.04 and `pool.snapshot.*` from 25.10 on.
 */
class ProtectionRepository(
    private val context: Context,
    private val sessions: Sessions,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ProtectionState())
    val state: StateFlow<ProtectionState> = _state.asStateFlow()
    private val lock = Mutex()

    init {
        scope.launch { sessions.active.collect { switchTo(it) } }
    }

    private suspend fun switchTo(active: ActiveServer?) = lock.withLock {
        if (active == null) {
            _state.value = ProtectionState()
            return@withLock
        }
        if (_state.value.serverId == active.server.id) return@withLock
        val cached = readCache(active.server.id)
        _state.value = ProtectionState(active.server.id, cached?.snapshot ?: ProtectionSnapshot(), cached?.updatedAt ?: 0)
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
                val snapshot = read(active.connection, caps, _state.value.snapshot)
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(snapshot = snapshot, updatedAt = now, error = null, loading = false)
                writeCache(active.server.id, ProtectionCache(now, snapshot))
            } catch (e: TnError) {
                _state.value = _state.value.copy(error = e, loading = false)
            }
        }
    }

    private suspend fun read(c: Connection, caps: Capabilities, previous: ProtectionSnapshot): ProtectionSnapshot = coroutineScope {
        fun opt(method: String) = async {
            if (!caps.has(method)) return@async null
            try {
                c.call(method)
            } catch (e: TnError.Forbidden) {
                null
            } catch (e: TnError.MethodMissing) {
                null
            } catch (e: TnError.Call) {
                null
            }
        }
        val snaps = opt("pool.snapshottask.query")
        val repl = opt("replication.query")
        val cloud = opt("cloudsync.query")
        val rsync = opt("rsynctask.query")
        val scrubs = opt("pool.scrub.query")

        val snapshotTasks = snaps.await().arr()?.mapNotNull(SnapshotTask::from).orEmpty()
        val replications = repl.await().arr()?.mapNotNull(ReplicationTask::from).orEmpty()
        val transfers = cloud.await().arr()?.mapNotNull(TransferTask::cloud).orEmpty() +
            rsync.await().arr()?.mapNotNull(TransferTask::rsync).orEmpty()

        // TrueNAS only keeps the latest run; remember each task's last success to show its age.
        val success = previous.lastSuccess.toMutableMap()
        snapshotTasks.forEach { t -> t.last?.takeIf { it.succeeded }?.atMs?.let { success[successKey("snapshot", t.id)] = it } }
        replications.forEach { t -> t.last?.takeIf { it.succeeded }?.atMs?.let { success[successKey("replication", t.id)] = it } }
        transfers.forEach { t -> t.last?.takeIf { it.succeeded }?.atMs?.let { success[successKey(t.kind.name, t.id)] = it } }

        ProtectionSnapshot(
            snapshotTasks = snapshotTasks,
            replications = replications,
            transfers = transfers,
            scrubs = scrubs.await().arr()?.mapNotNull(ScrubSchedule::from).orEmpty(),
            lastSuccess = success,
        )
    }

    private fun conn(): Connection = sessions.active.value?.connection ?: throw TnError.Unreachable(null)

    private fun caps(): Capabilities = sessions.access.value.capabilities ?: throw TnError.Unreachable(null)

    // --- Task actions ---

    suspend fun setSnapshotTaskEnabled(id: Int, enabled: Boolean) {
        conn().call("pool.snapshottask.update", listOf(JsonPrimitive(id), buildJsonObject { put("enabled", enabled) }))
        refresh()
    }

    suspend fun setScrubEnabled(id: Int, enabled: Boolean) {
        conn().call("pool.scrub.update", listOf(JsonPrimitive(id), buildJsonObject { put("enabled", enabled) }))
        refresh()
    }

    /** Takes the task's snapshot now. */
    suspend fun runSnapshotTask(id: Int) {
        conn().call("pool.snapshottask.run", listOf(JsonPrimitive(id)))
        refresh()
    }

    suspend fun runReplication(id: Int) {
        val c = conn()
        Jobs.awaitStart(c, c.call("replication.run", listOf(JsonPrimitive(id))))
        refresh()
    }

    suspend fun runTransfer(task: TransferTask) {
        val c = conn()
        val method = if (task.kind == CloudKind.CLOUD_SYNC) "cloudsync.sync" else "rsynctask.run"
        Jobs.awaitStart(c, c.call(method, listOf(JsonPrimitive(task.id))))
        refresh()
    }

    suspend fun abortCloudSync(id: Int) {
        conn().call("cloudsync.abort", listOf(JsonPrimitive(id)))
        refresh()
    }

    // --- Snapshots ---

    /** The snapshot namespace for this TrueNAS version, or null when this user can't see snapshots. */
    fun snapshotNamespace(): String? = sessions.access.value.capabilities?.snapshotNamespace

    /** Snapshots of [dataset] (not its children), newest first. */
    suspend fun snapshots(dataset: String): List<SnapshotInfo> {
        val ns = snapshotNamespace() ?: return emptyList()
        val c = conn()
        val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("dataset"), JsonPrimitive("="), JsonPrimitive(dataset)))))
        val withProps = buildJsonObject {
            putJsonObject("extra") {
                putJsonArray("properties") {
                    add(JsonPrimitive("used"))
                    add(JsonPrimitive("referenced"))
                    add(JsonPrimitive("creation"))
                    add(JsonPrimitive("userrefs"))
                }
            }
        }
        // Asking for just a few properties keeps the answer small; older versions that don't take
        // the list get the plain query.
        val result = try {
            c.call("$ns.query", listOf(filter, withProps))
        } catch (e: TnError.Call) {
            c.call("$ns.query", listOf(filter))
        }
        return result.arr()?.mapNotNull(SnapshotInfo::from).orEmpty()
            .sortedWith(compareByDescending<SnapshotInfo> { it.createdMs ?: 0 }.thenByDescending { it.name })
    }

    suspend fun createSnapshot(dataset: String, name: String, recursive: Boolean) {
        val ns = snapshotNamespace() ?: throw TnError.Forbidden(null)
        conn().call(
            "$ns.create",
            listOf(buildJsonObject {
                put("dataset", dataset)
                put("name", name)
                put("recursive", recursive)
            }),
        )
    }

    /** Deletes one snapshot, or several at once through core.bulk (a job). */
    suspend fun deleteSnapshots(ids: List<String>) {
        val ns = snapshotNamespace() ?: throw TnError.Forbidden(null)
        val c = conn()
        if (ids.size == 1) {
            c.call("$ns.delete", listOf(JsonPrimitive(ids.first())))
            return
        }
        val params = JsonArray(ids.map { JsonArray(listOf(JsonPrimitive(it))) })
        val result = Jobs.awaitResult(c, c.call("core.bulk", listOf(JsonPrimitive("$ns.delete"), params)), timeoutMs = 300_000)
        // core.bulk reports each call: [{result, error}, …]
        val errors = result.arr()?.mapNotNull { r -> r["error"].str() }?.filter { it.isNotBlank() }.orEmpty()
        if (errors.isNotEmpty()) throw TnError.Call(null, "${errors.size} of ${ids.size}: ${errors.first()}")
    }

    /**
     * Rolls [dataset] back to [snapshotId]. With [destroyNewer], newer snapshots are destroyed
     * (ZFS can only roll back to the latest snapshot otherwise).
     */
    suspend fun rollback(snapshotId: String, destroyNewer: Boolean) {
        val ns = snapshotNamespace() ?: throw TnError.Forbidden(null)
        conn().call("$ns.rollback", listOf(JsonPrimitive(snapshotId), buildJsonObject { put("recursive", destroyNewer) }))
    }

    /** Clones [snapshotId] into a new dataset [target]. */
    suspend fun clone(snapshotId: String, target: String) {
        val ns = snapshotNamespace() ?: throw TnError.Forbidden(null)
        conn().call("$ns.clone", listOf(buildJsonObject { put("snapshot", snapshotId); put("dataset_dst", target) }))
    }

    /** Whether this user may do [what] with snapshots on this version (25.04 needs Full Admin for rollback and clone). */
    fun canSnapshot(what: SnapshotAction): Boolean {
        val caps = sessions.access.value.capabilities ?: return false
        val ns = caps.snapshotNamespace ?: return false
        val legacy = ns == "zfs.snapshot"
        return when (what) {
            SnapshotAction.CREATE -> caps.has("$ns.create") && sessions.canWrite("SNAPSHOT_WRITE")
            SnapshotAction.DELETE -> caps.has("$ns.delete") && sessions.canWrite("SNAPSHOT_DELETE")
            SnapshotAction.ROLLBACK -> caps.has("$ns.rollback") &&
                if (legacy) sessions.canWrite(Capabilities.FULL_ADMIN) else sessions.canWrite("SNAPSHOT_WRITE", "POOL_WRITE")
            SnapshotAction.CLONE -> caps.has("$ns.clone") &&
                if (legacy) sessions.canWrite(Capabilities.FULL_ADMIN) else sessions.canWrite("SNAPSHOT_WRITE", "DATASET_WRITE")
        }
    }

    fun forget(serverId: String) {
        runCatching { cacheFile(serverId).delete() }
    }

    private fun cacheFile(serverId: String) = File(File(context.noBackupFilesDir, "cache").apply { mkdirs() }, "protection-$serverId.json")

    private suspend fun readCache(serverId: String): ProtectionCache? = withContext(Dispatchers.IO) {
        runCatching { TnJson.decodeFromString(ProtectionCache.serializer(), cacheFile(serverId).readText()) }.getOrNull()
    }

    private suspend fun writeCache(serverId: String, cache: ProtectionCache) = withContext(Dispatchers.IO) {
        runCatching { cacheFile(serverId).writeText(TnJson.encodeToString(ProtectionCache.serializer(), cache)) }
    }
}

enum class SnapshotAction { CREATE, DELETE, ROLLBACK, CLONE }
