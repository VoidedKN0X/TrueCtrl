package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.ServiceInfo
import com.vknxworks.truectrl.data.overview.epochMs
import com.vknxworks.truectrl.data.protection.Schedule
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.server.Jobs
import com.vknxworks.truectrl.data.server.Sessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream

// Everything below is read leniently, like the rest of the app: a missing or new field never crashes.

/** A virtual machine from vm.query. */
data class VmInfo(
    val id: Int,
    val name: String,
    val description: String,
    /** RUNNING, STOPPED or SUSPENDED. */
    val state: String,
    val vcpus: Int,
    val cores: Int,
    val threads: Int,
    /** Memory in MiB. */
    val memoryMib: Long,
    val autostart: Boolean,
) {
    val running: Boolean get() = state == "RUNNING"

    /** Virtual CPUs the guest sees: sockets × cores × threads. */
    val cpuCount: Int get() = (vcpus.coerceAtLeast(1)) * (cores.coerceAtLeast(1)) * (threads.coerceAtLeast(1))

    companion object {
        fun from(j: JsonElement): VmInfo? {
            val o = j.obj() ?: return null
            return VmInfo(
                id = o["id"].long()?.toInt() ?: return null,
                name = o["name"].str() ?: return null,
                description = o["description"].str().orEmpty(),
                state = o["status"]["state"].str().orEmpty(),
                vcpus = o["vcpus"].long()?.toInt() ?: 1,
                cores = o["cores"].long()?.toInt() ?: 1,
                threads = o["threads"].long()?.toInt() ?: 1,
                memoryMib = o["memory"].long() ?: 0,
                autostart = o["autostart"].bool() ?: false,
            )
        }
    }
}

/** A container (or an instance VM) from virt.instance.query, TrueNAS 25.04 and 25.10. */
data class InstanceInfo(
    val id: String,
    val name: String,
    /** CONTAINER or VM. */
    val type: String,
    /** RUNNING, STOPPED, UNKNOWN, ERROR, FROZEN, STARTING, STOPPING, FREEZING, THAWED, ABORTING. */
    val status: String,
    val cpu: String?,
    /** Memory limit in bytes; null when it has none. */
    val memory: Long?,
    val autostart: Boolean,
    val addresses: List<String>,
    /** What it was made from, like "debian trixie". */
    val image: String,
) {
    val running: Boolean get() = status == "RUNNING"

    companion object {
        fun from(j: JsonElement): InstanceInfo? {
            val o = j.obj() ?: return null
            val id = o["id"].str() ?: return null
            val image = o["image"]
            return InstanceInfo(
                id = id,
                name = o["name"].str() ?: id,
                type = o["type"].str().orEmpty(),
                status = o["status"].str().orEmpty(),
                cpu = o["cpu"].str()?.takeIf { it.isNotBlank() },
                memory = o["memory"].long()?.takeIf { it > 0 },
                autostart = o["autostart"].bool() ?: false,
                addresses = o["aliases"].arr()?.mapNotNull { it["address"].str() }.orEmpty(),
                image = listOfNotNull(image["os"].str(), image["release"].str()).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { image["description"].str().orEmpty() },
            )
        }
    }
}

enum class GuestVerb { START, STOP, POWER_OFF, RESTART }

/** Virtual machines and containers together; a null list means this server or user doesn't have that kind. */
data class Guests(val vms: List<VmInfo>?, val instances: List<InstanceInfo>?)

/** A TrueNAS user from user.query. Password hashes are never read. */
data class UserInfo(
    val id: Int,
    val uid: Long,
    val username: String,
    val fullName: String,
    val builtin: Boolean,
    val local: Boolean,
    val locked: Boolean,
    val passwordDisabled: Boolean,
    val smb: Boolean,
    val sshPassword: Boolean,
    val hasSshKey: Boolean,
    val home: String,
    val shell: String,
    val email: String?,
    /** The primary group's name. */
    val group: String,
    /** API IDs of the other groups. */
    val groupIds: List<Int>,
    val roles: List<String>,
    val twoFactor: Boolean,
    val sudo: Boolean,
    val apiKeys: Int,
    val passwordChangedMs: Long?,
) {
    companion object {
        fun from(j: JsonElement): UserInfo? {
            val o = j.obj() ?: return null
            return UserInfo(
                id = o["id"].long()?.toInt() ?: return null,
                uid = o["uid"].long() ?: 0,
                username = o["username"].str() ?: return null,
                fullName = o["full_name"].str().orEmpty(),
                builtin = o["builtin"].bool() ?: false,
                local = o["local"].bool() ?: true,
                locked = o["locked"].bool() ?: false,
                passwordDisabled = o["password_disabled"].bool() ?: false,
                smb = o["smb"].bool() ?: false,
                sshPassword = o["ssh_password_enabled"].bool() ?: false,
                hasSshKey = !o["sshpubkey"].str().isNullOrBlank(),
                home = o["home"].str().orEmpty(),
                shell = o["shell"].str().orEmpty(),
                email = o["email"].str()?.takeIf { it.isNotBlank() },
                group = o["group"]["bsdgrp_group"].str() ?: o["group"]["name"].str().orEmpty(),
                groupIds = o["groups"].arr()?.mapNotNull { it.long()?.toInt() }.orEmpty(),
                roles = o["roles"].arr()?.mapNotNull { it.str() }.orEmpty(),
                twoFactor = o["twofactor_auth_configured"].bool() ?: false,
                sudo = !o["sudo_commands"].arr().isNullOrEmpty() || !o["sudo_commands_nopasswd"].arr().isNullOrEmpty(),
                apiKeys = o["api_keys"].arr()?.size ?: 0,
                passwordChangedMs = o["last_password_change"].epochMs(),
            )
        }
    }
}

/** A TrueNAS group from group.query. */
data class GroupInfo(
    val id: Int,
    val gid: Long,
    val name: String,
    val builtin: Boolean,
    val local: Boolean,
    val smb: Boolean,
    val roles: List<String>,
    /** API IDs of its local members. */
    val userIds: List<Int>,
    val sudo: Boolean,
) {
    companion object {
        fun from(j: JsonElement): GroupInfo? {
            val o = j.obj() ?: return null
            return GroupInfo(
                id = o["id"].long()?.toInt() ?: return null,
                gid = o["gid"].long() ?: 0,
                name = o["name"].str() ?: o["group"].str() ?: return null,
                builtin = o["builtin"].bool() ?: false,
                local = o["local"].bool() ?: true,
                smb = o["smb"].bool() ?: false,
                roles = o["roles"].arr()?.mapNotNull { it.str() }.orEmpty(),
                userIds = o["users"].arr()?.mapNotNull { it.long()?.toInt() }.orEmpty(),
                sudo = !o["sudo_commands"].arr().isNullOrEmpty() || !o["sudo_commands_nopasswd"].arr().isNullOrEmpty(),
            )
        }
    }
}

data class Accounts(val users: List<UserInfo>, val groups: List<GroupInfo>)

/** The UPS service: how it's set up, whether it runs, and the UPS's latest readings from reporting. */
data class UpsStatus(
    val service: ServiceInfo?,
    val identifier: String,
    val description: String,
    /** MASTER (the UPS is plugged into this server) or SLAVE (another server shares it). */
    val mode: String,
    val driver: String,
    val port: String,
    val remoteHost: String,
    /** LOWBATT (shut down when the battery is low) or BATT (when it goes on battery). */
    val shutdown: String,
    val shutdownTimer: Long?,
    /** Battery charge in percent. */
    val charge: Double?,
    /** Estimated runtime on battery, in seconds. */
    val runtimeSeconds: Double?,
    /** Load in percent. */
    val load: Double?,
    val inputVoltage: Double?,
    val batteryVoltage: Double?,
    val outputVoltage: Double?,
    val temperature: Double?,
) {
    val hasReadings: Boolean
        get() = listOf(charge, runtimeSeconds, load, inputVoltage, batteryVoltage, outputVoltage, temperature).any { it != null }
}

data class IscsiLun(val lunId: Int, val extent: IscsiExtent?)

data class IscsiGroup(val portal: String, val initiators: String?, val authMethod: String)

data class IscsiTarget(
    val id: Int,
    val name: String,
    val alias: String?,
    /** ISCSI, FC or BOTH. */
    val mode: String,
    val groups: List<IscsiGroup>,
    val luns: List<IscsiLun>,
    val networks: List<String>,
)

data class IscsiExtent(
    val id: Int,
    val name: String,
    /** DISK (a zvol) or FILE. */
    val type: String,
    /** The zvol ("zvol/tank/vm-disk") or the file's path. */
    val source: String,
    val enabled: Boolean,
    val readOnly: Boolean,
    val comment: String,
    val locked: Boolean,
)

data class IscsiSession(val initiator: String, val address: String, val alias: String?, val target: String)

data class IscsiOverview(
    val service: ServiceInfo?,
    val basename: String,
    val listenPort: Int?,
    val targets: List<IscsiTarget>,
    val extents: List<IscsiExtent>,
    val sessions: List<IscsiSession>?,
)

/** One line of the audit log (audit.query). */
data class AuditEntry(
    val id: String,
    val atMs: Long?,
    val username: String,
    val address: String,
    /** MIDDLEWARE, SMB, SUDO or SYSTEM. */
    val service: String,
    val event: String,
    /** What happened in words, when TrueNAS says so (the method's description for a middleware call). */
    val summary: String?,
    val success: Boolean,
    /** event_data as TrueNAS sent it (it redacts secrets itself), for the detail view. */
    val data: JsonElement?,
) {
    companion object {
        fun from(j: JsonElement, index: Int): AuditEntry? {
            val o = j.obj() ?: return null
            val data = o["event_data"]
            return AuditEntry(
                id = o["audit_id"].str() ?: "row-$index",
                atMs = o["timestamp"].epochMs() ?: o["message_timestamp"].long()?.let { it * 1000 },
                username = o["username"].str().orEmpty(),
                address = o["address"].str().orEmpty(),
                service = o["service"].str().orEmpty(),
                event = o["event"].str().orEmpty(),
                summary = (data["description"].str() ?: data["method"].str() ?: data["proctitle"].str())?.takeIf { it.isNotBlank() },
                success = o["success"].bool() ?: true,
                data = data,
            )
        }
    }
}

/** A boot environment (boot.environment.query): one installed TrueNAS version the server can start from. */
data class BootEnv(
    val id: String,
    /** The one running now. */
    val active: Boolean,
    /** The one the next boot uses. */
    val activated: Boolean,
    val createdMs: Long?,
    val usedBytes: Long,
    /** Protected from being cleaned up by the updater. */
    val keep: Boolean,
    val canActivate: Boolean,
) {
    companion object {
        fun from(j: JsonElement): BootEnv? {
            val o = j.obj() ?: return null
            return BootEnv(
                id = o["id"].str() ?: return null,
                active = o["active"].bool() ?: false,
                activated = o["activated"].bool() ?: false,
                createdMs = o["created"].epochMs(),
                usedBytes = o["used_bytes"].long() ?: 0,
                keep = o["keep"].bool() ?: false,
                canActivate = o["can_activate"].bool() ?: false,
            )
        }
    }
}

/** A cron job (cronjob.query). */
data class CronJob(
    val id: Int,
    val enabled: Boolean,
    val command: String,
    val description: String,
    val user: String,
    val schedule: Schedule?,
) {
    companion object {
        fun from(j: JsonElement): CronJob? {
            val o = j.obj() ?: return null
            return CronJob(
                id = o["id"].long()?.toInt() ?: return null,
                enabled = o["enabled"].bool() ?: true,
                command = o["command"].str().orEmpty(),
                description = o["description"].str().orEmpty(),
                user = o["user"].str().orEmpty(),
                schedule = Schedule.from(o["schedule"]),
            )
        }
    }
}

/**
 * The System tab's administration screens: virtual machines and containers, users and groups, the
 * UPS, iSCSI, the audit log, boot environments, cron jobs and the configuration backup. Like
 * [SystemRepository], everything is read when its screen opens instead of being cached.
 *
 * Method names and arguments are the ones documented for TrueNAS 25.10 (api.truenas.com/v25.10)
 * and, for virt.instance, its middleware source; each feature is only offered when
 * core.get_methods lists its method for this user.
 */
class AdminRepository(private val sessions: Sessions) {

    private fun conn(): Connection = sessions.active.value?.connection ?: throw TnError.Unreachable(null)

    private fun caps(): Capabilities? = sessions.access.value.capabilities

    fun has(method: String): Boolean = caps()?.has(method) == true

    /** [method] exists for this user and they may change things with every one of [roles]. */
    fun canDo(method: String, vararg roles: String): Boolean = has(method) && sessions.canWrite(*roles)

    private suspend fun list(method: String, vararg params: JsonElement): JsonArray? = conn().call(method, params.toList()).arr()

    private suspend fun service(name: String): ServiceInfo? = runCatching {
        val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("service"), JsonPrimitive("="), JsonPrimitive(name)))))
        list("service.query", filter)?.firstNotNullOfOrNull(ServiceInfo::from)
    }.getOrElse { if (it is CancellationException) throw it else null }

    // --- Virtual machines and containers ---

    suspend fun guests(): Guests {
        val vms = if (has("vm.query")) list("vm.query")?.mapNotNull(VmInfo::from).orEmpty().sortedBy { it.name.lowercase() } else null
        // Containers are optional: a server where they were never set up answers with an error or nothing.
        val instances = if (has("virt.instance.query")) {
            try {
                list("virt.instance.query")?.mapNotNull(InstanceInfo::from).orEmpty().sortedBy { it.name.lowercase() }
            } catch (e: TnError.Call) {
                if (vms == null) throw e else emptyList()
            }
        } else {
            null
        }
        return Guests(vms, instances)
    }

    /**
     * vm.start, vm.stop (asks the guest to shut down), vm.poweroff (pulls the plug) and vm.restart.
     * Stop and restart are jobs; an early failure is thrown, otherwise the action counts as started.
     */
    suspend fun vm(id: Int, verb: GuestVerb) {
        val c = conn()
        val vmId = JsonPrimitive(id)
        val result = when (verb) {
            GuestVerb.START -> c.call("vm.start", listOf(vmId, buildJsonObject { put("overcommit", false) }))
            GuestVerb.STOP -> c.call("vm.stop", listOf(vmId, buildJsonObject { put("force", false); put("force_after_timeout", false) }))
            GuestVerb.POWER_OFF -> c.call("vm.poweroff", listOf(vmId))
            GuestVerb.RESTART -> c.call("vm.restart", listOf(vmId))
        }
        Jobs.awaitStart(c, result, graceMs = 5_000)
    }

    /**
     * virt.instance.start / stop / restart (all jobs). A stop without force must name a timeout, so
     * the guest gets [GRACEFUL_SECONDS] to shut down; POWER_OFF stops it at once.
     */
    suspend fun instance(id: String, verb: GuestVerb) {
        val c = conn()
        val instanceId = JsonPrimitive(id)
        val graceful = buildJsonObject { put("timeout", GRACEFUL_SECONDS); put("force", false) }
        val result = when (verb) {
            GuestVerb.START -> c.call("virt.instance.start", listOf(instanceId))
            GuestVerb.STOP -> c.call("virt.instance.stop", listOf(instanceId, graceful))
            GuestVerb.POWER_OFF -> c.call("virt.instance.stop", listOf(instanceId, buildJsonObject { put("timeout", -1); put("force", true) }))
            GuestVerb.RESTART -> c.call("virt.instance.restart", listOf(instanceId, graceful))
        }
        Jobs.awaitStart(c, result, graceMs = 5_000)
    }

    // --- Users and groups ---

    suspend fun accounts(): Accounts {
        val users = if (has("user.query")) list("user.query")?.mapNotNull(UserInfo::from).orEmpty() else emptyList()
        val groups = if (has("group.query")) list("group.query")?.mapNotNull(GroupInfo::from).orEmpty() else emptyList()
        return Accounts(users.sortedBy { it.username.lowercase() }, groups.sortedBy { it.name.lowercase() })
    }

    // --- UPS ---

    suspend fun ups(): UpsStatus {
        val c = conn()
        val config = c.call("ups.config")
        val dataMethod = "reporting.netdata_get_data".takeIf(::has)

        /** The newest value of one UPS graph, or null when the UPS doesn't report it (or isn't running). */
        suspend fun latest(graph: String, identifier: String? = null): Double? {
            val method = dataMethod ?: return null
            return try {
                val graphs = JsonArray(listOf(buildJsonObject { put("name", graph); if (identifier != null) put("identifier", identifier) }))
                val query = buildJsonObject { put("unit", "HOUR"); put("page", 1); put("aggregate", false) }
                val rows = c.call(method, listOf(graphs, query)).arr()?.firstOrNull()?.get("data").arr().orEmpty()
                // Rows are [time, value]; the newest rows can still be empty.
                rows.asReversed().firstNotNullOfOrNull { row -> row.arr()?.getOrNull(1).double() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

        val service = service("ups")
        // Without the service nothing is measured: don't ask for graphs that can't exist.
        val running = service?.running == true
        return UpsStatus(
            service = service,
            identifier = config["identifier"].str().orEmpty(),
            description = config["description"].str().orEmpty(),
            mode = config["mode"].str().orEmpty(),
            driver = config["driver"].str().orEmpty(),
            port = config["port"].str().orEmpty(),
            remoteHost = config["remotehost"].str().orEmpty(),
            shutdown = config["shutdown"].str().orEmpty(),
            shutdownTimer = config["shutdowntimer"].long(),
            charge = if (running) latest("upscharge") else null,
            runtimeSeconds = if (running) latest("upsruntime") else null,
            load = if (running) latest("upsload") else null,
            inputVoltage = if (running) latest("upsvoltage", "input") else null,
            batteryVoltage = if (running) latest("upsvoltage", "battery") else null,
            outputVoltage = if (running) latest("upsvoltage", "output") else null,
            temperature = if (running) latest("upstemperature") else null,
        )
    }

    // --- iSCSI ---

    suspend fun iscsi(): IscsiOverview {
        /** A list this user may not see (or this version lacks) is simply empty. */
        suspend fun opt(method: String): JsonArray? = if (!has(method)) null else try {
            list(method)
        } catch (e: TnError.Forbidden) {
            null
        } catch (e: TnError.MethodMissing) {
            null
        }

        val targets = list("iscsi.target.query").orEmpty()
        val extents = opt("iscsi.extent.query").orEmpty().mapNotNull { j ->
            val o = j.obj() ?: return@mapNotNull null
            IscsiExtent(
                id = o["id"].long()?.toInt() ?: return@mapNotNull null,
                name = o["name"].str().orEmpty(),
                type = o["type"].str().orEmpty(),
                source = (o["disk"].str() ?: o["path"].str()).orEmpty(),
                enabled = o["enabled"].bool() ?: true,
                readOnly = o["ro"].bool() ?: false,
                comment = o["comment"].str().orEmpty(),
                locked = o["locked"].bool() ?: false,
            )
        }
        val extentById = extents.associateBy { it.id }
        val links = opt("iscsi.targetextent.query").orEmpty()
        val portals = opt("iscsi.portal.query").orEmpty().mapNotNull { p ->
            val id = p["id"].long()?.toInt() ?: return@mapNotNull null
            id to p["listen"].arr().orEmpty().mapNotNull { l ->
                val ip = l["ip"].str() ?: return@mapNotNull null
                l["port"].long()?.let { "$ip:$it" } ?: ip
            }.joinToString(", ")
        }.toMap()
        val initiators = opt("iscsi.initiator.query").orEmpty().mapNotNull { i ->
            val id = i["id"].long()?.toInt() ?: return@mapNotNull null
            id to i["initiators"].arr().orEmpty().mapNotNull { it.str() }.joinToString(", ")
        }.toMap()
        val global = if (has("iscsi.global.config")) runCatching { conn().call("iscsi.global.config") }.getOrElse { if (it is CancellationException) throw it else null } else null
        val sessions = if (has("iscsi.global.sessions")) {
            try {
                list("iscsi.global.sessions")?.mapNotNull { s ->
                    IscsiSession(
                        initiator = s["initiator"].str() ?: return@mapNotNull null,
                        address = s["initiator_addr"].str().orEmpty(),
                        alias = s["initiator_alias"].str()?.takeIf { it.isNotBlank() },
                        target = s["target"].str().orEmpty(),
                    )
                }
            } catch (e: TnError.Forbidden) {
                null
            } catch (e: TnError.Call) {
                // The service isn't running: nobody is connected.
                emptyList()
            }
        } else {
            null
        }

        return IscsiOverview(
            service = service("iscsitarget"),
            basename = global["basename"].str().orEmpty(),
            listenPort = global["listen_port"].long()?.toInt(),
            targets = targets.mapNotNull { t ->
                val id = t["id"].long()?.toInt() ?: return@mapNotNull null
                IscsiTarget(
                    id = id,
                    name = t["name"].str().orEmpty(),
                    alias = t["alias"].str()?.takeIf { it.isNotBlank() },
                    mode = t["mode"].str().orEmpty(),
                    groups = t["groups"].arr().orEmpty().map { g ->
                        val portalId = g["portal"].long()?.toInt()
                        IscsiGroup(
                            portal = portals[portalId]?.takeIf { it.isNotBlank() } ?: portalId?.toString().orEmpty(),
                            initiators = g["initiator"].long()?.toInt()?.let { initiators[it] },
                            authMethod = g["authmethod"].str().orEmpty(),
                        )
                    },
                    luns = links.filter { it["target"].long()?.toInt() == id }.map { l ->
                        IscsiLun(l["lunid"].long()?.toInt() ?: 0, extentById[l["extent"].long()?.toInt()])
                    }.sortedBy { it.lunId },
                    networks = t["auth_networks"].arr().orEmpty().mapNotNull { it.str() },
                )
            }.sortedBy { it.name.lowercase() },
            extents = extents.sortedBy { it.name.lowercase() },
            sessions = sessions,
        )
    }

    // --- Audit log ---

    /** [limit] audit entries of [service], newest first, starting at [offset]; only failed ones with [failedOnly]. */
    suspend fun audit(service: String, failedOnly: Boolean, offset: Int, limit: Int = AUDIT_PAGE): List<AuditEntry> {
        val data = buildJsonObject {
            putJsonArray("services") { add(JsonPrimitive(service)) }
            putJsonArray("query-filters") {
                if (failedOnly) add(JsonArray(listOf(JsonPrimitive("success"), JsonPrimitive("="), JsonPrimitive(false))))
            }
            putJsonObject("query-options") {
                putJsonArray("order_by") { add(JsonPrimitive("-message_timestamp")) }
                put("offset", offset)
                put("limit", limit)
            }
        }
        return conn().call("audit.query", listOf(data), waitMs = 15_000).arr()
            ?.mapIndexedNotNull { i, j -> AuditEntry.from(j, offset + i) }.orEmpty()
    }

    // --- Boot environments ---

    suspend fun bootEnvs(): List<BootEnv> =
        list("boot.environment.query")?.mapNotNull(BootEnv::from).orEmpty().sortedByDescending { it.createdMs ?: 0 }

    /** Makes [id] the one the next boot starts from. */
    suspend fun activateBootEnv(id: String) {
        conn().call("boot.environment.activate", listOf(buildJsonObject { put("id", id) }))
    }

    /** Protects [id] from the updater's clean-up, or stops protecting it. */
    suspend fun keepBootEnv(id: String, keep: Boolean) {
        conn().call("boot.environment.keep", listOf(buildJsonObject { put("id", id); put("value", keep) }))
    }

    /** Deletes [id] for good. TrueNAS refuses the running one and the one for the next boot. */
    suspend fun destroyBootEnv(id: String) {
        conn().call("boot.environment.destroy", listOf(buildJsonObject { put("id", id) }))
    }

    // --- Cron jobs ---

    suspend fun cronJobs(): List<CronJob> = list("cronjob.query")?.mapNotNull(CronJob::from).orEmpty()

    suspend fun setCronEnabled(id: Int, enabled: Boolean) {
        conn().call("cronjob.update", listOf(JsonPrimitive(id), buildJsonObject { put("enabled", enabled) }))
    }

    /** Runs the cron job's command now, as a job (it shows under Jobs). */
    suspend fun runCron(id: Int) {
        val c = conn()
        Jobs.awaitStart(c, c.call("cronjob.run", listOf(JsonPrimitive(id), JsonPrimitive(false))), graceMs = 3_000)
    }

    // --- Configuration backup ---

    /**
     * Downloads the server's configuration (config.save through core.download) into the stream [open]
     * gives and returns the number of bytes written. With [secretSeed] or [rootKeys] TrueNAS makes a
     * tar file that also holds those; without them it's the configuration database alone. The
     * download address works once and is only fetched from this server, with its pinned certificate.
     * [open] runs on a background thread, and only once TrueNAS has answered.
     */
    suspend fun downloadConfig(secretSeed: Boolean, rootKeys: Boolean, fileName: String, open: () -> OutputStream): Long {
        val c = conn()
        val options = buildJsonObject {
            put("secretseed", secretSeed)
            put("pool_keys", false)
            put("root_authorized_keys", rootKeys)
        }
        val result = c.call(
            "core.download",
            listOf(JsonPrimitive("config.save"), JsonArray(listOf(options)), JsonPrimitive(fileName), JsonPrimitive(false)),
        )
        val path = result.arr()?.getOrNull(1).str()?.takeIf { it.startsWith("/") } ?: throw TnError.Call(null, null)
        return withContext(Dispatchers.IO) {
            try {
                c.download(path).use { response ->
                    if (!response.isSuccessful) throw TnError.Call(null, "HTTP ${response.code}")
                    val body = response.body ?: throw TnError.Call(null, null)
                    open().use { out -> body.byteStream().use { input -> input.copyTo(out) } }
                }
            } catch (e: TnError) {
                throw e
            } catch (e: java.io.IOException) {
                throw TnError.fromIo(e)
            }
        }
    }

    companion object {
        const val AUDIT_PAGE = 100

        /** How long a container gets to shut down by itself before TrueNAS gives up on a stop or restart. */
        const val GRACEFUL_SECONDS = 30

        val AUDIT_SERVICES = listOf("MIDDLEWARE", "SMB", "SUDO", "SYSTEM")
    }
}
