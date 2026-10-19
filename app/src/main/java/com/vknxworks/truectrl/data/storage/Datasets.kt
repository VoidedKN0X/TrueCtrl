package com.vknxworks.truectrl.data.storage

import android.content.Context
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
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

/** A dataset or zvol from pool.dataset.query. Sizes are bytes; 0 quota means none. */
@Serializable
data class DatasetInfo(
    val id: String,
    /** FILESYSTEM or VOLUME. */
    val type: String,
    val pool: String,
    val used: Long? = null,
    val available: Long? = null,
    val quota: Long = 0,
    val refquota: Long = 0,
    val reservation: Long = 0,
    val compression: String? = null,
    val compressRatio: String? = null,
    val recordsize: String? = null,
    val volsize: Long? = null,
    val mountpoint: String? = null,
    val comments: String = "",
    val encrypted: Boolean = false,
    val encryptionRoot: String? = null,
    val locked: Boolean = false,
    /** PASSPHRASE or HEX. */
    val keyFormat: String? = null,
    val readonly: Boolean = false,
) {
    val name: String get() = id.substringAfterLast('/')
    val depth: Int get() = id.count { it == '/' }
    val parent: String? get() = id.substringBeforeLast('/', "").ifBlank { null }
    val isZvol: Boolean get() = type == "VOLUME"
    val isRoot: Boolean get() = !id.contains('/')

    /** This dataset is where encryption is set (so it can be locked or unlocked on its own). */
    val isEncryptionRoot: Boolean get() = encrypted && (encryptionRoot == null || encryptionRoot == id)

    companion object {
        private fun bytes(j: JsonElement?): Long? = j["parsed"].long() ?: j["rawvalue"].str()?.toLongOrNull() ?: j.long()
        private fun text(j: JsonElement?): String? = j["value"].str() ?: j.str()

        fun from(j: JsonElement): DatasetInfo? {
            val o = j.obj() ?: return null
            val id = o["id"].str() ?: o["name"].str() ?: return null
            return DatasetInfo(
                id = id,
                type = o["type"].str() ?: "FILESYSTEM",
                pool = o["pool"].str() ?: id.substringBefore('/'),
                used = bytes(o["used"]),
                available = bytes(o["available"]),
                quota = bytes(o["quota"]) ?: 0,
                refquota = bytes(o["refquota"]) ?: 0,
                reservation = bytes(o["reservation"]) ?: 0,
                compression = text(o["compression"]),
                compressRatio = text(o["compressratio"]),
                recordsize = text(o["recordsize"]),
                volsize = bytes(o["volsize"]),
                mountpoint = o["mountpoint"].str(),
                comments = text(o["comments"]).orEmpty(),
                encrypted = o["encrypted"].bool() ?: false,
                encryptionRoot = o["encryption_root"].str(),
                locked = o["locked"].bool() ?: false,
                keyFormat = text(o["key_format"]),
                readonly = text(o["readonly"]).equals("ON", ignoreCase = true),
            )
        }
    }
}

/** What deleting a dataset would take with it, shown before asking for the typed confirmation. */
data class DeletePreview(
    val children: List<String>,
    val snapshots: Int?,
    /** Shares, tasks and apps using the dataset ("SMB share: media"). */
    val attachments: List<String>,
    /** Processes with files open on it. */
    val processes: List<String>,
)

/** Presets for a new dataset, as in the TrueNAS web UI. */
enum class DatasetPreset { GENERIC, SMB, NFS, MULTIPROTOCOL, APPS }

data class DatasetsState(
    val serverId: String? = null,
    val datasets: List<DatasetInfo> = emptyList(),
    val updatedAt: Long = 0,
    val error: TnError? = null,
    val loading: Boolean = false,
)

@Serializable
private data class DatasetsCache(val updatedAt: Long, val datasets: List<DatasetInfo>)

/** Datasets and zvols: the tree, and creating, editing, locking, unlocking and deleting them. */
class DatasetRepository(
    private val context: Context,
    private val sessions: Sessions,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DatasetsState())
    val state: StateFlow<DatasetsState> = _state.asStateFlow()
    private val lock = Mutex()

    init {
        scope.launch { sessions.active.collect { switchTo(it) } }
    }

    private suspend fun switchTo(active: ActiveServer?) = lock.withLock {
        if (active == null) {
            _state.value = DatasetsState()
            return@withLock
        }
        if (_state.value.serverId == active.server.id) return@withLock
        val cached = readCache(active.server.id)
        _state.value = DatasetsState(active.server.id, cached?.datasets.orEmpty(), cached?.updatedAt ?: 0)
    }

    suspend fun refresh() {
        val active = sessions.active.value ?: return
        lock.withLock {
            if (_state.value.serverId != active.server.id) return
            val caps = sessions.access.value.capabilities
            if (caps == null || !caps.has("pool.dataset.query")) {
                _state.value = _state.value.copy(error = sessions.access.value.error, loading = false)
                return
            }
            _state.value = _state.value.copy(loading = true)
            try {
                val options = buildJsonObject {
                    putJsonObject("extra") {
                        put("flat", true)
                        put("retrieve_children", false)
                    }
                }
                val list = active.connection.call("pool.dataset.query", listOf(JsonArray(emptyList()), options))
                    .arr()?.mapNotNull(DatasetInfo::from).orEmpty().sortedBy { it.id }
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(datasets = list, updatedAt = now, error = null, loading = false)
                writeCache(active.server.id, DatasetsCache(now, list))
            } catch (e: TnError) {
                _state.value = _state.value.copy(error = e, loading = false)
            }
        }
    }

    private fun conn(): Connection = sessions.active.value?.connection ?: throw TnError.Unreachable(null)

    private fun idArg(id: String) = JsonPrimitive(id)

    /** Creates a dataset (or a zvol of [volsizeBytes]). Names are full paths like "tank/media". */
    suspend fun create(path: String, preset: DatasetPreset, comments: String, volsizeBytes: Long? = null) {
        val body = buildJsonObject {
            put("name", path)
            if (volsizeBytes != null) {
                put("type", "VOLUME")
                put("volsize", volsizeBytes)
            } else {
                put("type", "FILESYSTEM")
                put("share_type", preset.name)
            }
            if (comments.isNotBlank()) put("comments", comments)
        }
        conn().call("pool.dataset.create", listOf(body))
        refresh()
    }

    /** Sets the quota (bytes, 0 = none; TrueNAS needs at least 1 GiB otherwise) and the comments. */
    suspend fun update(id: String, quotaBytes: Long?, comments: String?) {
        val body = buildJsonObject {
            if (quotaBytes != null) put("quota", quotaBytes)
            if (comments != null) put("comments", comments)
        }
        conn().call("pool.dataset.update", listOf(idArg(id), body))
        refresh()
    }

    suspend fun deletePreview(d: DatasetInfo): DeletePreview = coroutineScope {
        val c = conn()
        val children = _state.value.datasets.filter { it.id.startsWith(d.id + "/") }.map { it.id }
        val snapshots = async { runCatching { c.call("pool.dataset.snapshot_count", listOf(idArg(d.id))).long()?.toInt() }.getOrNull() }
        val attachments = async {
            runCatching {
                c.call("pool.dataset.attachments", listOf(idArg(d.id))).arr()?.flatMap { a ->
                    val type = a["type"].str().orEmpty()
                    val items = a["attachments"].arr()?.mapNotNull { it.str() }.orEmpty()
                    if (items.isEmpty()) listOf(type) else items.map { "$type: $it" }
                }
            }.getOrNull().orEmpty()
        }
        val processes = async {
            runCatching {
                c.call("pool.dataset.processes", listOf(idArg(d.id))).arr()?.mapNotNull { p ->
                    listOfNotNull(p["service"].str(), p["name"].str(), p["pid"].long()?.let { "($it)" }).joinToString(" ").takeIf { it.isNotBlank() }
                }
            }.getOrNull().orEmpty()
        }
        DeletePreview(children, snapshots.await(), attachments.await(), processes.await())
    }

    /** Deletes the dataset with all its children and snapshots. */
    suspend fun delete(id: String) {
        conn().call("pool.dataset.delete", listOf(idArg(id), buildJsonObject { put("recursive", true) }))
        refresh()
    }

    suspend fun lock(id: String) {
        val c = conn()
        Jobs.awaitResult(c, c.call("pool.dataset.lock", listOf(idArg(id))), timeoutMs = 120_000)
        refresh()
    }

    /**
     * Unlocks an encrypted dataset (and its children that share the key) with [passphrase]. The
     * passphrase is only sent; it's never kept. Throws when TrueNAS refuses it.
     */
    suspend fun unlock(id: String, passphrase: CharArray) {
        val c = conn()
        val options = buildJsonObject {
            put("recursive", true)
            putJsonArray("datasets") {
                add(buildJsonObject {
                    put("name", id)
                    put("passphrase", String(passphrase))
                })
            }
        }
        passphrase.fill(' ')
        val result = Jobs.awaitResult(c, c.call("pool.dataset.unlock", listOf(idArg(id), options)), timeoutMs = 120_000)
        val failed = result["failed"].obj()
        if (failed != null && failed.isNotEmpty()) {
            val reason = failed.values.firstOrNull()?.let { it["error"].str() ?: it.str() }
            throw TnError.Call("EINVAL", reason)
        }
        refresh()
    }

    fun forget(serverId: String) {
        runCatching { cacheFile(serverId).delete() }
    }

    private fun cacheFile(serverId: String) = File(File(context.noBackupFilesDir, "cache").apply { mkdirs() }, "datasets-$serverId.json")

    private suspend fun readCache(serverId: String): DatasetsCache? = withContext(Dispatchers.IO) {
        runCatching { TnJson.decodeFromString(DatasetsCache.serializer(), cacheFile(serverId).readText()) }.getOrNull()
    }

    private suspend fun writeCache(serverId: String, cache: DatasetsCache) = withContext(Dispatchers.IO) {
        runCatching { cacheFile(serverId).writeText(TnJson.encodeToString(DatasetsCache.serializer(), cache)) }
    }
}
