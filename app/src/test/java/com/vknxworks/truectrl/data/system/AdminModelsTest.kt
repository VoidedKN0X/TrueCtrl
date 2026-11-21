package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.overview.Attention
import com.vknxworks.truectrl.data.overview.AttentionItem
import com.vknxworks.truectrl.data.overview.OverviewSnapshot
import com.vknxworks.truectrl.data.overview.PoolInfo
import com.vknxworks.truectrl.data.storage.UnusedDisk
import com.vknxworks.truectrl.data.storage.Vdev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lenient readers of the administration screens, on answers shaped like the TrueNAS 25.10 API documentation. */
class AdminModelsTest {
    private fun json(text: String) = TnJson.parseToJsonElement(text.trimIndent())

    @Test
    fun vmReadsStateAndCountsCpus() {
        val vm = VmInfo.from(json("""{"id": 3, "name": "haos", "vcpus": 1, "cores": 2, "threads": 2, "memory": 4096, "autostart": true, "status": {"state": "RUNNING", "pid": 12, "domain_state": "RUNNING"}}"""))!!
        assertTrue(vm.running)
        assertEquals(4, vm.cpuCount)
        assertEquals(4096L, vm.memoryMib)
        assertNull(VmInfo.from(json("""{"name": "no id"}""")))
    }

    @Test
    fun instanceReadsImageAndAddresses() {
        val i = InstanceInfo.from(
            json("""{"id": "web", "name": "web", "type": "CONTAINER", "status": "STOPPED", "cpu": null, "memory": null, "autostart": false,
                "aliases": [{"type": "INET", "address": "192.168.1.50", "netmask": 24}], "image": {"os": "Debian", "release": "trixie"}}"""),
        )!!
        assertFalse(i.running)
        assertEquals("Debian trixie", i.image)
        assertEquals(listOf("192.168.1.50"), i.addresses)
        assertNull(i.memory)
    }

    @Test
    fun userNeverNeedsHashesAndReadsGroups() {
        val u = UserInfo.from(
            json("""{"id": 70, "uid": 3000, "username": "truectrl", "full_name": "TrueCtrl", "builtin": false, "local": true, "locked": false,
                "password_disabled": true, "smb": false, "group": {"id": 114, "bsdgrp_gid": 3000, "bsdgrp_group": "truectrl"}, "groups": [40, 41],
                "roles": ["READONLY_ADMIN"], "sshpubkey": null, "sudo_commands": [], "sudo_commands_nopasswd": [], "api_keys": [1],
                "unixhash": "should-be-ignored"}"""),
        )!!
        assertEquals("truectrl", u.group)
        assertEquals(listOf(40, 41), u.groupIds)
        assertEquals(1, u.apiKeys)
        assertFalse(u.hasSshKey)
        assertFalse(u.sudo)
        assertTrue(u.passwordDisabled)
    }

    @Test
    fun bootEnvironmentAndCronJob() {
        val be = BootEnv.from(json("""{"id": "25.10.1", "dataset": "boot-pool/ROOT/25.10.1", "active": true, "activated": true, "created": {"${'$'}date": 1760000000000}, "used_bytes": 3221225472, "used": "3G", "keep": false, "can_activate": true}"""))!!
        assertTrue(be.active && be.activated)
        assertEquals(1760000000000L, be.createdMs)
        val cron = CronJob.from(json("""{"id": 2, "enabled": false, "command": "/mnt/tank/scripts/backup.sh", "description": "", "user": "root", "schedule": {"minute": "30", "hour": "3", "dom": "*", "month": "*", "dow": "*"}}"""))!!
        assertFalse(cron.enabled)
        assertEquals("30", cron.schedule?.minute)
    }

    @Test
    fun auditEntryTakesTheMethodDescription() {
        val e = AuditEntry.from(
            json("""{"audit_id": "abc", "message_timestamp": 1760000000, "timestamp": {"${'$'}date": 1760000000000}, "address": "192.168.1.20", "username": "admin",
                "session": "s", "service": "MIDDLEWARE", "service_data": null, "event": "METHOD_CALL",
                "event_data": {"method": "pool.scrub", "description": "Start scrub on tank", "authenticated": true}, "success": false}"""),
            0,
        )!!
        assertEquals("Start scrub on tank", e.summary)
        assertFalse(e.success)
        assertEquals(1760000000000L, e.atMs)
    }

    @Test
    fun unusedDiskAndVdevGuid() {
        val d = UnusedDisk.from(json("""{"name": "sdc", "devname": "sdc", "size": 4000787030016, "serial": "WD-1", "model": "WDC", "exported_zpool": "old", "imported_zpool": null, "duplicate_serial": []}"""))!!
        assertEquals("old", d.exportedPool)
        val v = Vdev.from(json("""{"type": "DISK", "name": "x", "guid": "80802394992848654", "status": "ONLINE", "disk": "sda", "children": []}"""))!!
        assertEquals("80802394992848654", v.guid)
    }

    @Test
    fun upgradablePoolIsOnlyNotedWhenHealthy() {
        val healthy = PoolInfo(id = 1, name = "tank", status = "ONLINE", healthy = true)
        val degraded = PoolInfo(id = 2, name = "old", status = "DEGRADED", healthy = false)
        val items = Attention.compute(OverviewSnapshot(pools = listOf(healthy, degraded), upgradablePools = listOf(1, 2)), poolWarnPercent = 80, now = 0L)
        assertEquals(listOf("tank"), items.filterIsInstance<AttentionItem.PoolUpgradable>().map { it.pool.name })
    }
}
