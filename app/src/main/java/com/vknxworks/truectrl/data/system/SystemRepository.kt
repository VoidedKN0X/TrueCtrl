package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.UpdateApi
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.epochMs
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.server.Jobs
import com.vknxworks.truectrl.data.server.Sessions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A container of an app, for its logs. */
data class AppContainer(val id: String, val service: String, val state: String, val image: String)

/** An installed app with what the System › Apps screen needs. */
data class AppDetail(
    val name: String,
    /** RUNNING, DEPLOYING, STOPPED, STOPPING, CRASHED. */
    val state: String,
    val version: String,
    val upgradeAvailable: Boolean,
    val latestVersion: String?,
    val imageUpdates: Boolean,
    val customApp: Boolean,
    /** Web UI links: name → URL. */
    val portals: Map<String, String>,
    val containers: List<AppContainer>,
) {
    companion object {
        fun from(j: JsonElement): AppDetail? {
            val o = j.obj() ?: return null
            val name = o["name"].str() ?: o["id"].str() ?: return null
            return AppDetail(
                name = name,
                state = o["state"].str().orEmpty(),
                version = o["human_version"].str() ?: o["version"].str().orEmpty(),
                upgradeAvailable = o["upgrade_available"].bool() ?: false,
                latestVersion = o["latest_version"].str(),
                imageUpdates = o["image_updates_available"].bool() ?: false,
                customApp = o["custom_app"].bool() ?: false,
                portals = o["portals"].obj()?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap().orEmpty(),
                containers = o["active_workloads"]["container_details"].arr()?.mapNotNull { c ->
                    val id = c["id"].str() ?: return@mapNotNull null
                    AppContainer(id, c["service_name"].str().orEmpty(), c["state"].str().orEmpty(), c["image"].str().orEmpty())
                }.orEmpty(),
            )
        }
    }
}

/** A TrueNAS job from core.get_jobs. */
data class JobInfo(
    val id: Long,
    val method: String,
    val description: String?,
    /** WAITING, RUNNING, SUCCESS, FAILED, ABORTED. */
    val state: String,
    val percent: Double?,
    val progressText: String?,
    val error: String?,
    val abortable: Boolean,
    val startedMs: Long?,
    val finishedMs: Long?,
) {
    val active: Boolean get() = state == "RUNNING" || state == "WAITING"

    companion object {
        fun from(j: JsonElement): JobInfo? {
            val o = j.obj() ?: return null
            return JobInfo(
                id = o["id"].long() ?: return null,
                method = o["method"].str().orEmpty(),
                description = o["description"].str()?.takeIf { it.isNotBlank() },
                state = o["state"].str().orEmpty(),
                percent = o["progress"]["percent"].double(),
                progressText = o["progress"]["description"].str()?.takeIf { it.isNotBlank() },
                error = o["error"].str()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
                abortable = o["abortable"].bool() ?: false,
                startedMs = o["time_started"].epochMs(),
                finishedMs = o["time_finished"].epochMs(),
            )
        }
    }
}

/** What the Updates screen shows. */
data class UpdateStatus(
    val current: String?,
    val newVersion: String?,
    val releaseNotesUrl: String?,
    /** Download already running, 0–100. */
    val downloadPercent: Double?,
    /** TrueNAS couldn't check (no internet, update server down…). */
    val error: String?,
    /** 25.04: an update was applied and waits for a reboot. */
    val rebootRequired: Boolean = false,
)

/** A network interface with its addresses. */
data class NetInterface(
    val name: String,
    val type: String,
    val description: String,
    val linkUp: Boolean,
    val mtu: Int?,
    val addresses: List<String>,
    val dhcp: Boolean,
)

/** Live traffic per interface, bytes per second. */
data class NetRate(val rx: Double, val tx: Double, val speedMbps: Long?)

enum class AppVerb { START, STOP, REDEPLOY, UPGRADE }

/**
 * The System tab's actions: apps, jobs, updates, network and power. Data here is read when a
 * screen opens (and polled while it's visible) rather than cached, as it changes often.
 */
class SystemRepository(private val sessions: Sessions) {

    private fun conn(): Connection = sessions.active.value?.connection ?: throw TnError.Unreachable(null)

    // --- Apps ---

    suspend fun apps(): List<AppDetail> =
        conn().call("app.query").arr()?.mapNotNull(AppDetail::from).orEmpty().sortedBy { it.name.lowercase() }

    /** Starts, stops, redeploys or upgrades an app and waits for the job (up to 10 minutes). */
    suspend fun app(name: String, verb: AppVerb) {
        val c = conn()
        val args: List<JsonElement> = when (verb) {
            AppVerb.UPGRADE -> listOf(JsonPrimitive(name), buildJsonObject { put("app_version", "latest") })
            else -> listOf(JsonPrimitive(name))
        }
        val method = "app." + verb.name.lowercase()
        Jobs.awaitResult(c, c.call(method, args), timeoutMs = 600_000)
    }

    /**
     * Live log lines of one container, starting with the last [tail] lines. Each event is one
     * line; the subscription ends when collection stops.
     */
    fun containerLogs(app: String, container: String, tail: Int = 500): Flow<String> {
        val c = sessions.active.value?.connection ?: return emptyFlow()
        val args = buildJsonObject {
            put("app_name", app)
            put("container_id", container)
            put("tail_lines", tail)
        }
        return c.subscribe("app.container_log_follow:$args").mapNotNull { e ->
            if (e.msg == "unsubscribed") null else e.fields?.get("data").str()?.trimEnd('\n', '\r')
        }
    }

    // --- Jobs ---

    suspend fun jobs(): List<JobInfo> {
        val options = buildJsonObject {
            put("order_by", JsonArray(listOf(JsonPrimitive("-id"))))
            put("limit", 100)
        }
        return conn().call("core.get_jobs", listOf(JsonArray(emptyList()), options)).arr()?.mapNotNull(JobInfo::from).orEmpty()
    }

    suspend fun abortJob(id: Long) {
        conn().call("core.job_abort", listOf(JsonPrimitive(id)))
    }

    // --- Updates ---

    suspend fun updateStatus(): UpdateStatus? {
        val caps = sessions.access.value.capabilities ?: return null
        val c = conn()
        return when (caps.updateApi) {
            UpdateApi.PROFILES -> {
                val r = c.call("update.status")
                val status = r["status"]
                UpdateStatus(
                    current = sessions.access.value.capabilities?.version?.label,
                    newVersion = status["new_version"]["version"].str(),
                    releaseNotesUrl = status["new_version"]["release_notes_url"].str(),
                    downloadPercent = r["update_download_progress"]["percent"].double(),
                    error = if (r["code"].str() == "ERROR") r["error"]["reason"].str() ?: r["error"]["errname"].str() else null,
                )
            }
            UpdateApi.LEGACY -> {
                val r = c.call("update.check_available")
                when (r["status"].str()) {
                    "AVAILABLE" -> UpdateStatus(
                        current = sessions.access.value.capabilities?.version?.label,
                        newVersion = r["version"].str(),
                        releaseNotesUrl = r["notes"].str()?.takeIf { it.startsWith("http") } ?: r["release_notes_url"].str(),
                        downloadPercent = null,
                        error = null,
                    )
                    "REBOOT_REQUIRED" -> UpdateStatus(sessions.access.value.capabilities?.version?.label, null, null, null, null, rebootRequired = true)
                    else -> UpdateStatus(sessions.access.value.capabilities?.version?.label, null, null, null, null)
                }
            }
            UpdateApi.NONE -> null
        }
    }

    /** update.run (25.10+) or update.update (25.04), as a job. TrueNAS reboots afterwards when [reboot]. */
    suspend fun runUpdate(reboot: Boolean) {
        val caps = sessions.access.value.capabilities ?: throw TnError.Unreachable(null)
        val method = when {
            caps.has("update.run") -> "update.run"
            caps.has("update.update") -> "update.update"
            else -> throw TnError.MethodMissing("update.run")
        }
        val c = conn()
        // Downloading and applying can take a long time; a reboot ends the connection anyway.
        Jobs.awaitStart(c, c.call(method, listOf(buildJsonObject { put("reboot", reboot) })), graceMs = 8_000)
    }

    fun updateMethodAvailable(): Boolean {
        val caps = sessions.access.value.capabilities ?: return false
        return caps.has("update.run") || caps.has("update.update")
    }

    // --- Network ---

    suspend fun interfaces(): List<NetInterface> =
        conn().call("interface.query").arr()?.mapNotNull { j ->
            val o = j.obj() ?: return@mapNotNull null
            val name = o["name"].str() ?: return@mapNotNull null
            val state = o["state"]
            val aliases = (state["aliases"].arr() ?: o["aliases"].arr()).orEmpty().mapNotNull { a ->
                val type = a["type"].str()
                if (type != null && type != "INET" && type != "INET6") return@mapNotNull null
                val addr = a["address"].str() ?: return@mapNotNull null
                // Skip IPv6 link-local addresses: not useful to reach the server.
                if (addr.startsWith("fe80", ignoreCase = true)) return@mapNotNull null
                val mask = a["netmask"].long()?.toString() ?: a["netmask"].str()
                if (mask.isNullOrBlank()) addr else "$addr/$mask"
            }
            NetInterface(
                name = name,
                type = o["type"].str().orEmpty(),
                description = o["description"].str().orEmpty(),
                linkUp = state["link_state"].str()?.contains("UP", ignoreCase = true) == true,
                mtu = (state["mtu"].long() ?: o["mtu"].long())?.toInt(),
                addresses = aliases,
                dhcp = o["ipv4_dhcp"].bool() ?: false,
            )
        }.orEmpty().sortedBy { it.name }

    /** reporting.realtime per interface, every 2 seconds while collected. */
    fun netRates(): Flow<Map<String, NetRate>> {
        val c = sessions.active.value?.connection ?: return emptyFlow()
        return c.subscribe("reporting.realtime:{\"interval\":2}").mapNotNull { e ->
            val ifaces = e.fields?.get("interfaces") as? JsonObject ?: return@mapNotNull null
            ifaces.mapValues { (_, v) ->
                NetRate(
                    rx = v["received_bytes_rate"].double() ?: 0.0,
                    tx = v["sent_bytes_rate"].double() ?: 0.0,
                    speedMbps = v["speed"].long()?.takeIf { it > 0 },
                )
            }
        }
    }

    // --- Power ---

    /** system.reboot / system.shutdown (Full Admin). The connection drops right after. */
    suspend fun power(shutdown: Boolean) {
        val c = conn()
        val method = if (shutdown) "system.shutdown" else "system.reboot"
        val result = c.call(method, listOf(JsonPrimitive("Requested from TrueCtrl"), buildJsonObject { }))
        runCatching { Jobs.awaitStart(c, result, graceMs = 3_000) }
    }
}
