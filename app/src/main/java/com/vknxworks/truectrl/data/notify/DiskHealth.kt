package com.vknxworks.truectrl.data.notify

import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.AlertInfo
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.storage.PoolDetail
import com.vknxworks.truectrl.data.storage.SmartInfo
import com.vknxworks.truectrl.data.storage.SmartTest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** What a disk-health check found wrong. */
enum class DiskProblem {
    /** A pool isn't ONLINE (DEGRADED, FAULTED, …) or TrueNAS marks it unhealthy. */
    POOL_STATUS,

    /** A disk in a pool isn't ONLINE (FAULTED, REMOVED, UNAVAIL, OFFLINE, DEGRADED). */
    DISK_STATE,

    /** A disk in a pool has read, write or checksum errors. */
    DISK_ERRORS,

    /** The disk's latest SMART self-test failed (TrueNAS 25.04). */
    SMART_TEST,

    /** A TrueNAS alert about disks: SMART, disk temperature, pool or boot pool status. */
    ALERT,
}

/** One problem. [key] stays the same while the problem lasts, so it's notified once. */
data class DiskFinding(
    val key: String,
    val problem: DiskProblem,
    val severity: Severity,
    /** The pool or disk it's about. */
    val subject: String,
    /** The pool the disk is in, for disk problems. */
    val pool: String? = null,
    /** The status (FAULTED…), the error counts as "read/write/checksum", the test result or the alert text. */
    val detail: String? = null,
)

/** What was read for one check. */
data class DiskHealthInput(
    val pools: List<PoolDetail> = emptyList(),
    val alerts: List<AlertInfo> = emptyList(),
    /** SMART per disk name (TrueNAS 25.04; empty on later versions, which report SMART through alerts). */
    val smart: Map<String, SmartInfo> = emptyMap(),
)

/** What a server's disks looked like at the last check, kept per server. */
@Serializable
data class DiskHealthState(
    /** Keys of the problems found last time (and already notified). */
    val active: Set<String> = emptySet(),
    val lastCheckAt: Long = 0,
    /** "ok" or "failed". */
    val lastResult: String = "",
)

/**
 * The disk-health rules: turns pools, alerts and SMART results into [DiskFinding]s, and works out which
 * ones are new since the last check. Pure, so it's unit-tested.
 */
object DiskHealth {
    /**
     * TrueNAS alert classes about disks. Alert class names drop the "AlertClass"/"Alert" suffix, and they
     * changed between versions (`SMART` on 25.04; `SMARTFailedSelfTest`, `SMARTUncorrectedErrors`, … later),
     * so they're matched by prefix.
     */
    private val DISK_ALERT_PREFIXES = listOf("SMART", "Smartd", "DiskTemperature", "VolumeStatus", "BootPoolStatus", "DiskFailure", "Disk Failure")

    fun isDiskAlert(klass: String): Boolean {
        val k = klass.removeSuffix("AlertClass").removeSuffix("Alert")
        return DISK_ALERT_PREFIXES.any { k.startsWith(it, ignoreCase = true) }
    }

    /** Statuses of a pool or disk that are fine. */
    private val OK = setOf("ONLINE", "")

    fun findings(input: DiskHealthInput): List<DiskFinding> {
        val out = LinkedHashMap<String, DiskFinding>()
        fun add(f: DiskFinding) { out.putIfAbsent(f.key, f) }

        for (pool in input.pools) {
            val info = pool.info
            if (info.status.uppercase() !in OK || !info.healthy) {
                add(
                    DiskFinding(
                        key = "pool:${info.name}:${info.status.uppercase()}",
                        problem = DiskProblem.POOL_STATUS,
                        severity = if (info.isOnline) Severity.WARNING else Severity.CRITICAL,
                        subject = info.name,
                        detail = info.statusDetail ?: info.status,
                    ),
                )
            }
            for (disk in pool.allDisks) {
                val name = disk.disk ?: disk.name
                val status = disk.status.uppercase()
                if (status !in OK) {
                    add(
                        DiskFinding(
                            key = "disk:${info.name}:$name:$status",
                            problem = DiskProblem.DISK_STATE,
                            severity = if (status == "DEGRADED") Severity.WARNING else Severity.CRITICAL,
                            subject = name,
                            pool = info.name,
                            detail = status,
                        ),
                    )
                }
                if (disk.errors > 0) {
                    add(
                        DiskFinding(
                            // The counts are part of the key: more errors later are a new warning.
                            key = "errors:${info.name}:$name:${disk.readErrors}/${disk.writeErrors}/${disk.checksumErrors}",
                            problem = DiskProblem.DISK_ERRORS,
                            severity = Severity.WARNING,
                            subject = name,
                            pool = info.name,
                            detail = "${disk.readErrors}/${disk.writeErrors}/${disk.checksumErrors}",
                        ),
                    )
                }
            }
        }

        for ((disk, smart) in input.smart.toSortedMap()) {
            val latest = latestTest(smart.tests) ?: continue
            if (latest.status.uppercase() == "FAILED") {
                add(
                    DiskFinding(
                        key = "smart:$disk:${latest.lifetime ?: 0}",
                        problem = DiskProblem.SMART_TEST,
                        severity = Severity.CRITICAL,
                        subject = disk,
                        detail = latest.statusText.ifBlank { latest.status },
                    ),
                )
            }
        }

        for (a in input.alerts) {
            if (a.dismissed || !isDiskAlert(a.klass) || a.severity == Severity.INFO) continue
            add(
                DiskFinding(
                    key = "alert:${a.uuid}",
                    problem = DiskProblem.ALERT,
                    severity = a.severity,
                    subject = a.klass,
                    detail = a.text,
                ),
            )
        }
        return out.values.sortedBy { it.severity.ordinal }
    }

    /** The newest self-test: smartctl lists the newest first; a test that's still running doesn't count. */
    fun latestTest(tests: List<SmartTest>): SmartTest? = tests.firstOrNull { it.status.uppercase() != "RUNNING" }

    /** The findings not notified yet, and the keys of problems that are gone. */
    fun diff(prev: Set<String>, now: List<DiskFinding>): Pair<List<DiskFinding>, Set<String>> {
        val keys = now.map { it.key }.toSet()
        return now.filter { it.key !in prev } to (prev - keys)
    }

    /** Reads pools, alerts and (25.04) SMART results. Methods this user can't call are skipped. */
    suspend fun read(c: Connection, caps: Capabilities): DiskHealthInput = coroutineScope {
        suspend fun opt(method: String): JsonElement? {
            if (!caps.has(method)) return null
            return try {
                c.call(method)
            } catch (e: TnError.Forbidden) {
                null
            } catch (e: TnError.MethodMissing) {
                null
            } catch (e: TnError.Call) {
                null
            }
        }
        val pools = async { opt("pool.query") }
        val alerts = async { opt("alert.list") }
        val smart = async { if (caps.hasSmartApi) opt("smart.test.results") else null }
        parse(pools.await(), alerts.await(), smart.await())
    }

    /** Builds the input from the raw answers of pool.query, alert.list and smart.test.results. */
    fun parse(pools: JsonElement?, alerts: JsonElement?, smart: JsonElement?): DiskHealthInput = DiskHealthInput(
        pools = pools.arr()?.mapNotNull(PoolDetail::from).orEmpty(),
        alerts = alerts.arr()?.mapNotNull(AlertInfo::from).orEmpty(),
        smart = smart.arr()?.mapNotNull { e ->
            val disk = e["disk"].str() ?: return@mapNotNull null
            disk to SmartInfo(
                tests = e["tests"].arr()?.mapNotNull(SmartTest::from).orEmpty(),
                runningProgress = e["current_test"]["progress"].long()?.toInt(),
            )
        }?.toMap().orEmpty(),
    )
}
