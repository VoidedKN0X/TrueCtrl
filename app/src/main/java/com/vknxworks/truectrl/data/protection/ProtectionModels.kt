package com.vknxworks.truectrl.data.protection

import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.ScrubSchedule
import com.vknxworks.truectrl.data.overview.epochMs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A cron schedule as TrueNAS stores it on tasks. */
@Serializable
data class Schedule(
    val minute: String = "00",
    val hour: String = "*",
    val dom: String = "*",
    val month: String = "*",
    val dow: String = "*",
) {
    companion object {
        fun from(j: JsonElement?): Schedule? {
            val o = j.obj() ?: return null
            return Schedule(
                minute = o["minute"].str() ?: "00",
                hour = o["hour"].str() ?: "*",
                dom = o["dom"].str() ?: "*",
                month = o["month"].str() ?: "*",
                dow = o["dow"].str() ?: "*",
            )
        }
    }
}

/** The last run of a task: its state, when, the error, and progress while it runs. */
@Serializable
data class LastRun(
    /** FINISHED/SUCCESS, ERROR/FAILED, RUNNING, WAITING, PENDING, HOLD, ABORTED… */
    val state: String = "",
    val atMs: Long? = null,
    val error: String? = null,
    /** 0–100 while running, when TrueNAS reports it. */
    val progress: Double? = null,
) {
    val succeeded: Boolean get() = state == "FINISHED" || state == "SUCCESS"
    val failed: Boolean get() = state == "ERROR" || state == "FAILED"
    val running: Boolean get() = state == "RUNNING" || state == "WAITING"

    companion object {
        /** Replication and periodic snapshot tasks: `state: {state, datetime, error, progress?}`. */
        fun fromState(j: JsonElement?): LastRun? {
            val o = j.obj() ?: return null
            return LastRun(
                state = o["state"].str().orEmpty(),
                atMs = o["datetime"].epochMs(),
                error = o["error"].str()?.takeIf { it.isNotBlank() },
                progress = o["progress"]["percent"].double() ?: o["progress"].double(),
            )
        }

        /** Cloud sync and rsync: the last job, `{state, time_started, time_finished, error, progress}`. */
        fun fromJob(j: JsonElement?): LastRun? {
            val o = j.obj() ?: return null
            return LastRun(
                state = o["state"].str().orEmpty(),
                atMs = o["time_finished"].epochMs() ?: o["time_started"].epochMs(),
                error = o["error"].str()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
                progress = o["progress"]["percent"].double(),
            )
        }
    }
}

@Serializable
data class SnapshotTask(
    val id: Int,
    val dataset: String,
    val recursive: Boolean,
    val lifetimeValue: Int,
    /** HOUR, DAY, WEEK, MONTH, YEAR. */
    val lifetimeUnit: String,
    val namingSchema: String,
    val schedule: Schedule?,
    val enabled: Boolean,
    val last: LastRun?,
) {
    companion object {
        fun from(j: JsonElement): SnapshotTask? {
            val o = j.obj() ?: return null
            return SnapshotTask(
                id = o["id"].long()?.toInt() ?: return null,
                dataset = o["dataset"].str().orEmpty(),
                recursive = o["recursive"].bool() ?: false,
                lifetimeValue = o["lifetime_value"].long()?.toInt() ?: 0,
                lifetimeUnit = o["lifetime_unit"].str().orEmpty(),
                namingSchema = o["naming_schema"].str().orEmpty(),
                schedule = Schedule.from(o["schedule"]),
                enabled = o["enabled"].bool() ?: true,
                last = LastRun.fromState(o["state"]),
            )
        }
    }
}

@Serializable
data class ReplicationTask(
    val id: Int,
    val name: String,
    val direction: String,
    val transport: String,
    val sources: List<String>,
    val target: String,
    val enabled: Boolean,
    val schedule: Schedule?,
    val last: LastRun?,
    val lastSnapshot: String? = null,
) {
    companion object {
        fun from(j: JsonElement): ReplicationTask? {
            val o = j.obj() ?: return null
            val state = o["state"]
            return ReplicationTask(
                id = o["id"].long()?.toInt() ?: return null,
                name = o["name"].str().orEmpty(),
                direction = o["direction"].str().orEmpty(),
                transport = o["transport"].str().orEmpty(),
                sources = o["source_datasets"].arr()?.mapNotNull { it.str() }.orEmpty(),
                target = o["target_dataset"].str().orEmpty(),
                enabled = o["enabled"].bool() ?: true,
                schedule = Schedule.from(o["schedule"]),
                // A running job's progress is more current than the stored state.
                last = LastRun.fromState(state)?.let { s ->
                    val jobProgress = o["job"]["progress"]["percent"].double()
                    if (s.running && jobProgress != null) s.copy(progress = jobProgress) else s
                },
                lastSnapshot = state["last_snapshot"].str(),
            )
        }
    }
}

enum class CloudKind { CLOUD_SYNC, RSYNC }

/** A cloud sync or rsync task. */
@Serializable
data class TransferTask(
    val kind: CloudKind,
    val id: Int,
    val name: String,
    val path: String,
    /** PUSH or PULL. */
    val direction: String,
    /** The other end: the cloud credential's name or the rsync remote host and path. */
    val remote: String,
    val enabled: Boolean,
    val schedule: Schedule?,
    val last: LastRun?,
) {
    companion object {
        fun cloud(j: JsonElement): TransferTask? {
            val o = j.obj() ?: return null
            val path = o["path"].str().orEmpty()
            return TransferTask(
                kind = CloudKind.CLOUD_SYNC,
                id = o["id"].long()?.toInt() ?: return null,
                name = o["description"].str()?.takeIf { it.isNotBlank() } ?: path,
                path = path,
                direction = o["direction"].str().orEmpty(),
                remote = listOfNotNull(
                    o["credentials"]["name"].str(),
                    o["attributes"]["bucket"].str()?.let { b -> b + (o["attributes"]["folder"].str()?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "") },
                ).joinToString(": "),
                enabled = o["enabled"].bool() ?: true,
                schedule = Schedule.from(o["schedule"]),
                last = LastRun.fromJob(o["job"]),
            )
        }

        fun rsync(j: JsonElement): TransferTask? {
            val o = j.obj() ?: return null
            val path = o["path"].str().orEmpty()
            val host = o["remotehost"].str().orEmpty()
            val remotePath = o["remotepath"].str() ?: o["remotemodule"].str() ?: ""
            return TransferTask(
                kind = CloudKind.RSYNC,
                id = o["id"].long()?.toInt() ?: return null,
                name = o["desc"].str()?.takeIf { it.isNotBlank() } ?: path,
                path = path,
                direction = o["direction"].str().orEmpty(),
                remote = listOf(host, remotePath).filter { it.isNotBlank() }.joinToString(":"),
                enabled = o["enabled"].bool() ?: true,
                schedule = Schedule.from(o["schedule"]),
                last = LastRun.fromJob(o["job"]),
            )
        }
    }
}

/** Everything on the Protection tab, kept between sessions. */
@Serializable
data class ProtectionSnapshot(
    val snapshotTasks: List<SnapshotTask> = emptyList(),
    val replications: List<ReplicationTask> = emptyList(),
    val transfers: List<TransferTask> = emptyList(),
    val scrubs: List<ScrubSchedule> = emptyList(),
    /** When each task last succeeded, remembered across runs ("kind:id" → ms), for the backup age. */
    val lastSuccess: Map<String, Long> = emptyMap(),
)

/** One ZFS snapshot. */
data class SnapshotInfo(
    val id: String,
    val dataset: String,
    val name: String,
    val createdMs: Long?,
    val used: Long?,
    val referenced: Long?,
    val holds: Boolean = false,
) {
    companion object {
        fun from(j: JsonElement): SnapshotInfo? {
            val o = j.obj() ?: return null
            val id = o["id"].str() ?: o["name"].str() ?: return null
            val props = o["properties"]
            fun bytes(name: String): Long? = props[name]["parsed"].long() ?: props[name]["rawvalue"].str()?.toLongOrNull()
            val created = props["creation"]["parsed"].epochMs()
                ?: props["creation"]["rawvalue"].str()?.toLongOrNull()?.let { it * 1000 }
            return SnapshotInfo(
                id = id,
                dataset = o["dataset"].str() ?: id.substringBefore('@'),
                name = o["snapshot_name"].str() ?: id.substringAfter('@'),
                createdMs = created,
                used = bytes("used"),
                referenced = bytes("referenced"),
                holds = (o["holds"].obj()?.isNotEmpty() == true) || (props["userrefs"]["parsed"].long() ?: 0) > 0,
            )
        }
    }
}
