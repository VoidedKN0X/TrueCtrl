package com.vknxworks.truectrl.data.sharing

import android.content.Context
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.ServiceInfo
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

enum class ShareKind { SMB, NFS }

/** An SMB or NFS share. NFS shares have no name; their path is shown instead. */
@Serializable
data class ShareInfo(
    val kind: ShareKind,
    val id: Int,
    val name: String,
    val path: String,
    val comment: String,
    val enabled: Boolean,
    val readonly: Boolean,
    /** The dataset is locked, so the share isn't available. */
    val locked: Boolean = false,
    /** SMB purpose (DEFAULT_SHARE, TIMEMACHINE_SHARE, …) or NFS networks and hosts. */
    val extra: String = "",
) {
    companion object {
        fun smb(j: JsonElement): ShareInfo? {
            val o = j.obj() ?: return null
            return ShareInfo(
                kind = ShareKind.SMB,
                id = o["id"].long()?.toInt() ?: return null,
                name = o["name"].str().orEmpty(),
                path = o["path"].str().orEmpty(),
                comment = o["comment"].str().orEmpty(),
                enabled = o["enabled"].bool() ?: true,
                // 25.04 calls it `ro`, 25.10 `readonly`.
                readonly = o["readonly"].bool() ?: o["ro"].bool() ?: false,
                locked = o["locked"].bool() ?: false,
                extra = o["purpose"].str().orEmpty(),
            )
        }

        fun nfs(j: JsonElement): ShareInfo? {
            val o = j.obj() ?: return null
            val path = o["path"].str() ?: o["paths"].arr()?.firstOrNull().str() ?: ""
            val clients = (o["networks"].arr().orEmpty() + o["hosts"].arr().orEmpty()).mapNotNull { it.str() }
            return ShareInfo(
                kind = ShareKind.NFS,
                id = o["id"].long()?.toInt() ?: return null,
                name = path,
                path = path,
                comment = o["comment"].str().orEmpty(),
                enabled = o["enabled"].bool() ?: true,
                readonly = o["readonly"].bool() ?: o["ro"].bool() ?: false,
                locked = o["locked"].bool() ?: false,
                extra = clients.joinToString(", "),
            )
        }
    }
}

/** Someone connected to an SMB share, from smb.status. */
@Serializable
data class SmbSession(val user: String, val machine: String, val share: String = "")

@Serializable
data class SharingSnapshot(
    val smb: List<ShareInfo> = emptyList(),
    val nfs: List<ShareInfo> = emptyList(),
    val services: List<ServiceInfo> = emptyList(),
    /** Null when smb.status isn't available to this user or version. */
    val smbSessions: List<SmbSession>? = null,
)

data class SharingState(
    val serverId: String? = null,
    val snapshot: SharingSnapshot = SharingSnapshot(),
    val updatedAt: Long = 0,
    val error: TnError? = null,
    val loading: Boolean = false,
)

@Serializable
private data class SharingCache(val updatedAt: Long, val snapshot: SharingSnapshot)

enum class ServiceVerb { START, STOP, RESTART }

/** SMB and NFS shares, the services behind them, and who's connected over SMB. */
class SharingRepository(
    private val context: Context,
    private val sessions: Sessions,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SharingState())
    val state: StateFlow<SharingState> = _state.asStateFlow()
    private val lock = Mutex()

    init {
        scope.launch { sessions.active.collect { switchTo(it) } }
    }

    private suspend fun switchTo(active: ActiveServer?) = lock.withLock {
        if (active == null) {
            _state.value = SharingState()
            return@withLock
        }
        if (_state.value.serverId == active.server.id) return@withLock
        val cached = readCache(active.server.id)
        _state.value = SharingState(active.server.id, cached?.snapshot ?: SharingSnapshot(), cached?.updatedAt ?: 0)
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
                val snapshot = coroutineScope {
                    val c = active.connection
                    fun opt(method: String, vararg args: JsonElement) = async {
                        if (!caps.has(method)) return@async null
                        try {
                            c.call(method, args.toList())
                        } catch (e: TnError.Forbidden) {
                            null
                        } catch (e: TnError.MethodMissing) {
                            null
                        } catch (e: TnError.Call) {
                            null
                        }
                    }
                    val smb = opt("sharing.smb.query")
                    val nfs = opt("sharing.nfs.query")
                    val services = opt("service.query")
                    val status = opt("smb.status", JsonPrimitive("SESSIONS"))
                    SharingSnapshot(
                        smb = smb.await().arr()?.mapNotNull(ShareInfo::smb).orEmpty().sortedBy { it.name.lowercase() },
                        nfs = nfs.await().arr()?.mapNotNull(ShareInfo::nfs).orEmpty().sortedBy { it.path },
                        services = services.await().arr()?.mapNotNull(ServiceInfo::from).orEmpty().sortedBy { it.service },
                        smbSessions = status.await()?.let { sessionsOf(it) },
                    )
                }
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(snapshot = snapshot, updatedAt = now, error = null, loading = false)
                writeCache(active.server.id, SharingCache(now, snapshot))
            } catch (e: TnError) {
                _state.value = _state.value.copy(error = e, loading = false)
            }
        }
    }

    /** smb.status("SESSIONS") is a list on some versions and a map keyed by session id on others. */
    private fun sessionsOf(j: JsonElement): List<SmbSession> {
        val items = j.arr() ?: j.obj()?.values?.toList() ?: return emptyList()
        return items.mapNotNull { s ->
            val o = s.obj() ?: return@mapNotNull null
            val user = o["username"].str() ?: o["uid"].str() ?: return@mapNotNull null
            SmbSession(
                user = user,
                machine = o["remote_machine"].str() ?: o["hostname"].str() ?: o["remote_address"].str().orEmpty(),
            )
        }
    }

    private fun conn(): Connection = sessions.active.value?.connection ?: throw TnError.Unreachable(null)

    suspend fun setShareEnabled(share: ShareInfo, enabled: Boolean) {
        val ns = if (share.kind == ShareKind.SMB) "sharing.smb" else "sharing.nfs"
        conn().call("$ns.update", listOf(JsonPrimitive(share.id), buildJsonObject { put("enabled", enabled) }))
        refresh()
    }

    /** Start automatically on boot. */
    suspend fun setServiceEnabled(service: ServiceInfo, enabled: Boolean) {
        conn().call("service.update", listOf(JsonPrimitive(service.id), buildJsonObject { put("enable", enabled) }))
        refresh()
    }

    /**
     * Starts, stops or restarts a service: `service.control(verb, service)` (a job) from 25.10 on,
     * `service.start/stop/restart(service)` on 25.04. Throws when TrueNAS reports it didn't work.
     */
    suspend fun control(service: ServiceInfo, verb: ServiceVerb) {
        val c = conn()
        val caps = sessions.access.value.capabilities ?: throw TnError.Unreachable(null)
        val options = buildJsonObject { put("silent", false) }
        val result = if (caps.hasServiceControl) {
            Jobs.awaitResult(c, c.call("service.control", listOf(JsonPrimitive(verb.name), JsonPrimitive(service.service), options)), timeoutMs = 120_000)
        } else {
            c.call("service." + verb.name.lowercase(), listOf(JsonPrimitive(service.service), options))
        }
        refresh()
        // Both return whether the service ended up in the wanted state.
        if (result is JsonPrimitive && result.bool() == false) throw TnError.Call(null, null)
    }

    fun forget(serverId: String) {
        runCatching { cacheFile(serverId).delete() }
    }

    private fun cacheFile(serverId: String) = File(File(context.noBackupFilesDir, "cache").apply { mkdirs() }, "sharing-$serverId.json")

    private suspend fun readCache(serverId: String): SharingCache? = withContext(Dispatchers.IO) {
        runCatching { TnJson.decodeFromString(SharingCache.serializer(), cacheFile(serverId).readText()) }.getOrNull()
    }

    private suspend fun writeCache(serverId: String, cache: SharingCache) = withContext(Dispatchers.IO) {
        runCatching { cacheFile(serverId).writeText(TnJson.encodeToString(SharingCache.serializer(), cache)) }
    }
}
