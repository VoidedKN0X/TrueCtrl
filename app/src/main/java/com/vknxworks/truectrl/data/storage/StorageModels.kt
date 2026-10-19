package com.vknxworks.truectrl.data.storage

import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.PoolInfo
import com.vknxworks.truectrl.data.overview.ScrubSchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One node of a pool's layout: a vdev (MIRROR, RAIDZ1, …) or a disk inside it. */
@Serializable
data class Vdev(
    /** MIRROR, RAIDZ1, RAIDZ2, RAIDZ3, DRAID1…, DISK, FILE, SPARE, REPLACING. */
    val type: String,
    val name: String,
    /** ONLINE, DEGRADED, FAULTED, OFFLINE, UNAVAIL, REMOVED. */
    val status: String,
    /** The disk name (sda) for a disk, when TrueNAS knows it. */
    val disk: String? = null,
    val readErrors: Long = 0,
    val writeErrors: Long = 0,
    val checksumErrors: Long = 0,
    val children: List<Vdev> = emptyList(),
) {
    val errors: Long get() = readErrors + writeErrors + checksumErrors
    val isDisk: Boolean get() = children.isEmpty() && (type == "DISK" || type == "FILE" || disk != null)

    /** Every disk in this vdev, at any depth. */
    fun disks(): List<Vdev> = if (children.isEmpty()) listOf(this) else children.flatMap { it.disks() }

    companion object {
        fun from(j: JsonElement): Vdev? {
            val o = j.obj() ?: return null
            val stats = o["stats"].obj()
            return Vdev(
                type = o["type"].str().orEmpty().uppercase(),
                name = o["name"].str().orEmpty(),
                status = o["status"].str().orEmpty(),
                disk = o["disk"].str() ?: o["unavail_disk"]["name"].str(),
                readErrors = stats?.get("read_errors").long() ?: 0,
                writeErrors = stats?.get("write_errors").long() ?: 0,
                checksumErrors = stats?.get("checksum_errors").long() ?: 0,
                children = o["children"].arr()?.mapNotNull(::from).orEmpty(),
            )
        }
    }
}

/** A pool with its layout, as the Storage tab shows it. */
@Serializable
data class PoolDetail(
    val info: PoolInfo,
    /** Vdev groups by role: data, log, cache, spare, special, dedup. */
    val topology: Map<String, List<Vdev>> = emptyMap(),
    /** Root dataset compression ratio, like "1.32x". */
    val compressRatio: String? = null,
    /** False when the pool can be upgraded to enable newer ZFS features. */
    val isUpgraded: Boolean? = null,
) {
    val allDisks: List<Vdev> get() = topology.values.flatten().flatMap { it.disks() }

    companion object {
        val GROUPS = listOf("data", "special", "dedup", "log", "cache", "spare")

        fun from(j: JsonElement): PoolDetail? {
            val info = PoolInfo.from(j) ?: return null
            val topo = j["topology"].obj()
            return PoolDetail(
                info = info,
                topology = GROUPS.associateWith { g -> topo?.get(g).arr()?.mapNotNull(Vdev::from).orEmpty() }
                    .filterValues { it.isNotEmpty() },
                isUpgraded = j["is_upgraded"].bool(),
            )
        }
    }
}

/** A physical disk from disk.query. */
@Serializable
data class DiskInfo(
    val identifier: String,
    val name: String,
    val serial: String = "",
    val model: String = "",
    val size: Long = 0,
    /** HDD or SSD. */
    val type: String = "",
    val rotationRate: Int? = null,
    /** The pool the disk belongs to, if any. */
    val pool: String? = null,
    val devname: String = "",
    val description: String = "",
    val bus: String = "",
) {
    companion object {
        fun from(j: JsonElement): DiskInfo? {
            val o = j.obj() ?: return null
            val name = o["name"].str() ?: return null
            return DiskInfo(
                identifier = o["identifier"].str() ?: name,
                name = name,
                serial = o["serial"].str().orEmpty(),
                model = o["model"].str().orEmpty(),
                size = o["size"].long() ?: 0,
                type = o["type"].str().orEmpty(),
                rotationRate = o["rotationrate"].long()?.toInt()?.takeIf { it > 0 },
                pool = o["pool"].str()?.takeIf { it.isNotBlank() },
                devname = o["devname"].str().orEmpty(),
                description = o["description"].str().orEmpty(),
                bus = o["bus"].str().orEmpty(),
            )
        }
    }
}

/** One SMART self-test result (TrueNAS 25.04). */
@Serializable
data class SmartTest(
    val description: String,
    /** SUCCESS, RUNNING, ABORTED, FAILED, … */
    val status: String,
    val statusText: String,
    /** Power-on hours when it ran. */
    val lifetime: Long? = null,
) {
    companion object {
        fun from(j: JsonElement): SmartTest? {
            val o = j.obj() ?: return null
            return SmartTest(
                description = o["description"].str().orEmpty(),
                status = o["status"].str().orEmpty(),
                statusText = o["status_verbose"].str() ?: o["status"].str().orEmpty(),
                lifetime = o["lifetime"].long(),
            )
        }
    }
}

/** SMART for one disk on TrueNAS 25.04: test history and a running test's progress. */
@Serializable
data class SmartInfo(val tests: List<SmartTest> = emptyList(), val runningProgress: Int? = null)

/** One SMART attribute (ATA disks), or a health value for NVMe/SCSI. */
data class SmartAttribute(val id: Int?, val name: String, val value: String, val worst: String?, val threshold: String?, val raw: String) {
    companion object {
        /** ATA disks return a table; NVMe and SCSI return a single object of named values. */
        fun list(j: JsonElement?): List<SmartAttribute> {
            j.arr()?.let { table ->
                return table.mapNotNull { e ->
                    val o = e.obj() ?: return@mapNotNull null
                    SmartAttribute(
                        id = o["id"].long()?.toInt(),
                        name = o["name"].str().orEmpty().replace('_', ' '),
                        value = o["value"].long()?.toString() ?: o["value"].str().orEmpty(),
                        worst = o["worst"].long()?.toString(),
                        threshold = o["thresh"].long()?.toString(),
                        raw = o["raw"]["string"].str() ?: o["raw"]["value"].long()?.toString().orEmpty(),
                    )
                }
            }
            val obj = j as? JsonObject ?: return emptyList()
            return obj.entries.mapNotNull { (k, v) ->
                val text = v.long()?.toString() ?: v.double()?.toString() ?: v.str() ?: return@mapNotNull null
                SmartAttribute(null, k.replace('_', ' ').replaceFirstChar { it.uppercase() }, text, null, null, text)
            }
        }
    }
}

/** Everything the Storage tab shows that's worth keeping between sessions. */
@Serializable
data class StorageSnapshot(
    val pools: List<PoolDetail> = emptyList(),
    val disks: List<DiskInfo> = emptyList(),
    /** Current temperature per disk name in °C; missing when the disk doesn't report one. */
    val temps: Map<String, Double> = emptyMap(),
    val scrubs: List<ScrubSchedule> = emptyList(),
    /** SMART per disk name (TrueNAS 25.04 only). */
    val smart: Map<String, SmartInfo> = emptyMap(),
)

/** Reads a disk temperature value: a number, or [temperature, critical] with thresholds. */
fun tempOf(j: JsonElement?): Double? = j.double() ?: j.arr()?.firstOrNull().double()
