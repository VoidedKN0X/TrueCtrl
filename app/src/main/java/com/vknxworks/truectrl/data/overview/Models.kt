package com.vknxworks.truectrl.data.overview

import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

// Everything the Overview shows, read leniently from TrueNAS (a missing or new field never breaks
// anything) and kept as a cache so the last known state can be shown while offline.

/**
 * TrueNAS sends dates as `{"$date": <ms since epoch>}`. Plain numbers (seconds or ms) and ISO
 * strings are accepted too, in case a version sends them differently.
 */
fun JsonElement?.epochMs(): Long? {
    if (this == null) return null
    this["\$date"].long()?.let { return it }
    val p = this as? JsonPrimitive ?: return null
    p.long()?.let { n -> return if (n < 100_000_000_000L) n * 1000 else n }
    return p.str()?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

@Serializable
data class ScanInfo(
    /** SCRUB or RESILVER. */
    val function: String = "",
    /** SCANNING, FINISHED or CANCELED. */
    val state: String = "",
    val startMs: Long? = null,
    val endMs: Long? = null,
    val percentage: Double = 0.0,
    val errors: Long = 0,
    val paused: Boolean = false,
    val secondsLeft: Long? = null,
) {
    val isRunning: Boolean get() = state == "SCANNING" && !paused
    val isScrub: Boolean get() = function == "SCRUB"

    companion object {
        fun from(j: JsonElement?): ScanInfo? {
            val o = j.obj() ?: return null
            return ScanInfo(
                function = o["function"].str().orEmpty(),
                state = o["state"].str().orEmpty(),
                startMs = o["start_time"].epochMs(),
                endMs = o["end_time"].epochMs(),
                percentage = o["percentage"].double() ?: 0.0,
                errors = o["errors"].long() ?: 0,
                paused = o["pause"].epochMs() != null,
                secondsLeft = o["total_secs_left"].long(),
            )
        }
    }
}

@Serializable
data class PoolInfo(
    val id: Int,
    val name: String,
    /** ONLINE, DEGRADED, FAULTED, OFFLINE, UNAVAIL, REMOVED. */
    val status: String,
    val healthy: Boolean,
    val warning: Boolean = false,
    val statusDetail: String? = null,
    val size: Long = 0,
    val allocated: Long = 0,
    val free: Long = 0,
    val fragmentation: String? = null,
    val scan: ScanInfo? = null,
) {
    val usedFraction: Double? get() = if (size > 0) allocated.toDouble() / size else null
    val isOnline: Boolean get() = status == "ONLINE"

    companion object {
        fun from(j: JsonElement): PoolInfo? {
            val o = j.obj() ?: return null
            val name = o["name"].str() ?: return null
            return PoolInfo(
                id = o["id"].long()?.toInt() ?: 0,
                name = name,
                status = o["status"].str().orEmpty(),
                healthy = o["healthy"].bool() ?: false,
                warning = o["warning"].bool() ?: false,
                statusDetail = o["status_detail"].str()?.takeIf { it.isNotBlank() },
                size = o["size"].long() ?: 0,
                allocated = o["allocated"].long() ?: 0,
                free = o["free"].long() ?: 0,
                fragmentation = o["fragmentation"].str(),
                scan = ScanInfo.from(o["scan"]),
            )
        }
    }
}

@Serializable
data class AlertInfo(
    val uuid: String,
    val klass: String = "",
    /** INFO, NOTICE, WARNING, ERROR, CRITICAL, ALERT, EMERGENCY. */
    val level: String = "",
    val text: String = "",
    val source: String = "",
    val atMs: Long? = null,
    val dismissed: Boolean = false,
) {
    val severity: Severity
        get() = when (level.uppercase()) {
            "ERROR", "CRITICAL", "ALERT", "EMERGENCY" -> Severity.CRITICAL
            "WARNING" -> Severity.WARNING
            else -> Severity.INFO
        }

    companion object {
        fun from(j: JsonElement?): AlertInfo? {
            val o = j.obj() ?: return null
            val uuid = o["uuid"].str() ?: o["id"].str() ?: return null
            return AlertInfo(
                uuid = uuid,
                klass = o["klass"].str().orEmpty(),
                level = o["level"].str().orEmpty(),
                // `formatted` has the alert's details filled in (with a little HTML); `text` is the template.
                text = (o["formatted"].str() ?: o["text"].str()).orEmpty(),
                source = o["source"].str().orEmpty(),
                atMs = o["last_occurrence"].epochMs() ?: o["datetime"].epochMs(),
                dismissed = o["dismissed"].bool() ?: false,
            )
        }
    }
}

enum class TaskKind { REPLICATION, SNAPSHOT, CLOUD_SYNC, RSYNC }

/** The last run of a data protection task. */
@Serializable
data class TaskRun(
    val kind: TaskKind,
    val id: Int,
    val name: String,
    val enabled: Boolean,
    /** As TrueNAS reports it: FINISHED/ERROR/RUNNING/… for replication and snapshots, SUCCESS/FAILED/… for jobs. */
    val state: String = "",
    val error: String? = null,
    val atMs: Long? = null,
) {
    val failed: Boolean get() = state == "ERROR" || state == "FAILED"

    companion object {
        /** Replication and periodic snapshot tasks: `state: {state, datetime, error}`. */
        fun fromState(kind: TaskKind, j: JsonElement, name: String): TaskRun? {
            val o = j.obj() ?: return null
            val s = o["state"].obj()
            return TaskRun(
                kind = kind,
                id = o["id"].long()?.toInt() ?: return null,
                name = name,
                enabled = o["enabled"].bool() ?: true,
                state = s?.get("state").str().orEmpty(),
                error = s?.get("error").str()?.takeIf { it.isNotBlank() },
                atMs = s?.get("datetime").epochMs(),
            )
        }

        /** Cloud sync and rsync tasks: the last job, `job: {state, time_finished, error}`. */
        fun fromJob(kind: TaskKind, j: JsonElement, name: String): TaskRun? {
            val o = j.obj() ?: return null
            val job = o["job"].obj()
            return TaskRun(
                kind = kind,
                id = o["id"].long()?.toInt() ?: return null,
                name = name,
                enabled = o["enabled"].bool() ?: true,
                state = job?.get("state").str().orEmpty(),
                error = job?.get("error").str()?.lineSequence()?.firstOrNull { it.isNotBlank() },
                atMs = job?.get("time_finished").epochMs() ?: job?.get("time_started").epochMs(),
            )
        }
    }
}

@Serializable
data class ServiceInfo(val id: Int, val service: String, val enable: Boolean, val state: String) {
    val running: Boolean get() = state == "RUNNING"

    companion object {
        fun from(j: JsonElement): ServiceInfo? {
            val o = j.obj() ?: return null
            return ServiceInfo(
                id = o["id"].long()?.toInt() ?: 0,
                service = o["service"].str() ?: return null,
                enable = o["enable"].bool() ?: false,
                state = o["state"].str().orEmpty(),
            )
        }
    }
}

@Serializable
data class AppInfo(val name: String, val state: String, val upgradeAvailable: Boolean = false) {
    companion object {
        fun from(j: JsonElement): AppInfo? {
            val o = j.obj() ?: return null
            return AppInfo(
                name = o["name"].str() ?: return null,
                state = o["state"].str().orEmpty(),
                upgradeAvailable = o["upgrade_available"].bool() ?: false,
            )
        }
    }
}

@Serializable
data class ScrubSchedule(
    val poolId: Int,
    val poolName: String,
    /** A scheduled scrub only runs when the last one is at least this many days old. */
    val thresholdDays: Int,
    val enabled: Boolean,
    val id: Int = 0,
    val minute: String = "00",
    val hour: String = "00",
    val dom: String = "*",
    val month: String = "*",
    val dow: String = "7",
) {
    companion object {
        fun from(j: JsonElement): ScrubSchedule? {
            val o = j.obj() ?: return null
            val c = o["schedule"].obj()
            return ScrubSchedule(
                poolId = o["pool"].long()?.toInt() ?: return null,
                poolName = o["pool_name"].str().orEmpty(),
                thresholdDays = o["threshold"].long()?.toInt() ?: 35,
                enabled = o["enabled"].bool() ?: true,
                id = o["id"].long()?.toInt() ?: 0,
                minute = c?.get("minute").str() ?: "00",
                hour = c?.get("hour").str() ?: "00",
                dom = c?.get("dom").str() ?: "*",
                month = c?.get("month").str() ?: "*",
                dow = c?.get("dow").str() ?: "*",
            )
        }
    }
}

@Serializable
data class UpdateInfo(
    /** A newer TrueNAS version can be installed. */
    val available: Boolean = false,
    val version: String? = null,
    /** An update was already applied and needs a reboot. */
    val applied: Boolean = false,
)

/** The basics from system.info, read leniently. */
@Serializable
data class SystemInfo(
    val hostname: String = "",
    val version: String = "",
    /** The CPU model. */
    val model: String = "",
    val cores: Int = 0,
    val physmem: Long = 0,
    val uptimeSeconds: Long = 0,
    /** When [uptimeSeconds] was read (ms), so uptime keeps counting while offline. */
    val readAtMs: Long = 0,
) {
    companion object {
        fun from(json: JsonElement?): SystemInfo? {
            val o = json.obj() ?: return null
            return SystemInfo(
                hostname = o["hostname"].str().orEmpty(),
                version = o["version"].str().orEmpty(),
                model = o["model"].str().orEmpty(),
                cores = o["cores"].long()?.toInt() ?: 0,
                physmem = o["physmem"].long() ?: 0,
                uptimeSeconds = o["uptime_seconds"].double()?.toLong() ?: 0,
                readAtMs = System.currentTimeMillis(),
            )
        }
    }
}

/** Everything the Overview shows that's worth keeping between sessions. */
@Serializable
data class OverviewSnapshot(
    val info: SystemInfo? = null,
    val pools: List<PoolInfo> = emptyList(),
    val alerts: List<AlertInfo> = emptyList(),
    val tasks: List<TaskRun> = emptyList(),
    val services: List<ServiceInfo> = emptyList(),
    val apps: List<AppInfo> = emptyList(),
    val scrubs: List<ScrubSchedule> = emptyList(),
    val update: UpdateInfo? = null,
    val rebootReasons: List<String> = emptyList(),
)

/** Reasons a reboot is pending, from system.reboot.info. */
fun rebootReasons(j: JsonElement?): List<String> =
    j["reboot_required_reasons"].arr()?.mapNotNull { r -> r["reason"].str() ?: r.str() }.orEmpty()
