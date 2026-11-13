package com.vknxworks.truectrl.data.notify

import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.overview.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskHealthTest {
    /** pool.query: one healthy pool, one DEGRADED mirror with a FAULTED disk and a disk with errors. */
    private val pools = """
        [
          {"id": 1, "name": "tank", "status": "ONLINE", "healthy": true, "warning": false, "status_detail": null,
           "topology": {"data": [{"type": "MIRROR", "name": "mirror-0", "status": "ONLINE",
             "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 0},
             "children": [
               {"type": "DISK", "name": "sda1", "disk": "sda", "status": "ONLINE", "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 0}, "children": []},
               {"type": "DISK", "name": "sdb1", "disk": "sdb", "status": "ONLINE", "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 0}, "children": []}
             ]}], "log": [], "cache": [], "spare": [], "special": [], "dedup": []}},
          {"id": 2, "name": "media", "status": "DEGRADED", "healthy": false, "warning": true,
           "status_detail": "One or more devices are faulted in response to persistent errors.",
           "topology": {"data": [{"type": "MIRROR", "name": "mirror-0", "status": "DEGRADED",
             "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 0},
             "children": [
               {"type": "DISK", "name": "sdc1", "disk": "sdc", "status": "FAULTED", "stats": {"read_errors": 3, "write_errors": 0, "checksum_errors": 12}, "children": []},
               {"type": "DISK", "name": "sdd1", "disk": "sdd", "status": "ONLINE", "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 1}, "children": []}
             ]}], "log": [], "cache": [], "spare": [], "special": [], "dedup": []}}
        ]
    """.trimIndent()

    /** alert.list: a SMART alert, a disk temperature alert (dismissed), a quota alert and an info SMART alert. */
    private val alerts = """
        [
          {"uuid": "a1", "klass": "SMART", "level": "CRITICAL", "formatted": "Device: /dev/sdc, 8 Currently unreadable (pending) sectors.", "dismissed": false},
          {"uuid": "a2", "klass": "DiskTemperatureTooHot", "level": "CRITICAL", "formatted": "Disk sdd is 61°C", "dismissed": true},
          {"uuid": "a3", "klass": "QuotaWarning", "level": "WARNING", "formatted": "Quota exceeded", "dismissed": false},
          {"uuid": "a4", "klass": "SMARTFailedSelfTest", "level": "INFO", "formatted": "info only", "dismissed": false},
          {"uuid": "a5", "klass": "VolumeStatus", "level": "CRITICAL", "formatted": "Pool media state is DEGRADED", "dismissed": false}
        ]
    """.trimIndent()

    /** smart.test.results (TrueNAS 25.04): sda's newest test failed; sdb has a running test over a passed one. */
    private val smart = """
        [
          {"disk": "sda", "tests": [
            {"num": 1, "description": "Short offline", "status": "FAILED", "status_verbose": "Completed: read failure", "lifetime": 41000},
            {"num": 2, "description": "Short offline", "status": "SUCCESS", "status_verbose": "Completed without error", "lifetime": 40000}
          ]},
          {"disk": "sdb", "tests": [
            {"num": 1, "description": "Extended offline", "status": "RUNNING", "status_verbose": "Self-test routine in progress"},
            {"num": 2, "description": "Short offline", "status": "SUCCESS", "status_verbose": "Completed without error", "lifetime": 39000}
          ], "current_test": {"progress": 30}}
        ]
    """.trimIndent()

    private fun input() = DiskHealth.parse(TnJson.parseToJsonElement(pools), TnJson.parseToJsonElement(alerts), TnJson.parseToJsonElement(smart))

    @Test
    fun findsEveryKindOfProblem() {
        val f = DiskHealth.findings(input())
        val keys = f.map { it.key }
        assertTrue("pool:media:DEGRADED" in keys)
        assertTrue("disk:media:sdc:FAULTED" in keys)
        assertTrue("errors:media:sdc:3/0/12" in keys)
        assertTrue("errors:media:sdd:0/0/1" in keys)
        assertTrue("smart:sda:41000" in keys)
        assertTrue("alert:a1" in keys)
        assertTrue("alert:a5" in keys)
        // Healthy pool, dismissed and non-disk alerts, info alerts and a running test aren't problems.
        assertFalse(keys.any { it.contains("tank") || it.contains("sdb") || it == "alert:a2" || it == "alert:a3" || it == "alert:a4" })
        assertEquals(7, f.size)
        // Most serious first.
        assertEquals(Severity.CRITICAL, f.first().severity)
        assertEquals(Severity.WARNING, f.last().severity)
    }

    @Test
    fun findingDetails() {
        val f = DiskHealth.findings(input()).associateBy { it.key }
        assertEquals(DiskProblem.DISK_STATE, f.getValue("disk:media:sdc:FAULTED").problem)
        assertEquals("media", f.getValue("disk:media:sdc:FAULTED").pool)
        assertEquals("3/0/12", f.getValue("errors:media:sdc:3/0/12").detail)
        assertEquals("Completed: read failure", f.getValue("smart:sda:41000").detail)
        assertEquals("One or more devices are faulted in response to persistent errors.", f.getValue("pool:media:DEGRADED").detail)
        // A pool that isn't ONLINE and a FAULTED disk are critical.
        assertEquals(Severity.CRITICAL, f.getValue("pool:media:DEGRADED").severity)
        assertEquals(Severity.CRITICAL, f.getValue("disk:media:sdc:FAULTED").severity)
    }

    @Test
    fun diskAlertClasses() {
        listOf("SMART", "SMARTAlertClass", "SMARTUncorrectedErrors", "SmartdTooManyAttempts", "DiskTemperatureTooHot", "VolumeStatus", "BootPoolStatus")
            .forEach { assertTrue(it, DiskHealth.isDiskAlert(it)) }
        listOf("QuotaWarning", "ZpoolCapacityWarning", "ScrubPaused", "")
            .forEach { assertFalse(it, DiskHealth.isDiskAlert(it)) }
    }

    @Test
    fun onlyNewProblemsAreNotifiedAndGoneOnesCleared() {
        val now = DiskHealth.findings(input())
        val (fresh, gone) = DiskHealth.diff(setOf("pool:media:DEGRADED", "smart:sdz:1"), now)
        assertEquals(now.size - 1, fresh.size)
        assertFalse(fresh.any { it.key == "pool:media:DEGRADED" })
        assertEquals(setOf("smart:sdz:1"), gone)
        // Nothing new the second time round.
        val (again, none) = DiskHealth.diff(now.map { it.key }.toSet(), now)
        assertTrue(again.isEmpty() && none.isEmpty())
    }

    @Test
    fun moreErrorsLaterAreANewWarning() {
        val before = DiskHealth.findings(input()).map { it.key }.toSet()
        val worse = DiskHealth.parse(TnJson.parseToJsonElement(pools.replace("\"checksum_errors\": 12", "\"checksum_errors\": 40")), null, null)
        val (fresh, _) = DiskHealth.diff(before, DiskHealth.findings(worse))
        assertEquals(listOf("errors:media:sdc:3/0/40"), fresh.map { it.key })
    }

    @Test
    fun missingOrOddAnswersAreFine() {
        assertTrue(DiskHealth.findings(DiskHealth.parse(null, null, null)).isEmpty())
        val odd = DiskHealth.parse(TnJson.parseToJsonElement("""[{"name": "x", "status": "ONLINE", "healthy": true, "topology": null}]"""), TnJson.parseToJsonElement("{}"), TnJson.parseToJsonElement("[1, 2]"))
        assertTrue(DiskHealth.findings(odd).isEmpty())
    }

    @Test
    fun latestTestSkipsARunningOne() {
        val s = input().smart.getValue("sdb")
        assertEquals("SUCCESS", DiskHealth.latestTest(s.tests)!!.status)
    }
}
