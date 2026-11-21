package com.vknxworks.truectrl.data.settings

import com.vknxworks.truectrl.data.notify.NotifyEvent
import com.vknxworks.truectrl.data.notify.NotifySettings
import com.vknxworks.truectrl.data.server.AuthMethod
import com.vknxworks.truectrl.data.server.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private val server = Server(id = "a", name = "NAS", url = "https://192.168.1.9", auth = AuthMethod.API_KEY, username = "truectrl", certPin = "AA:BB", httpAccepted = false)

    @Test
    fun exportHoldsNoSecretsOrTrust() {
        val text = SettingsBackups.export(AppSettings(appLock = true), NotifySettings(ntfyTopic = "secret-topic", ntfyCertPin = "CC:DD", ntfyServer = "https://ntfy.example"), listOf(server), "1.0.0", "now")
        assertFalse(text.contains("secret-topic"))
        assertFalse(text.contains("AA:BB"))
        assertFalse(text.contains("CC:DD"))
        assertFalse(text.contains("ntfy.example"))
        assertFalse(text.contains("appLock"))
        assertFalse(text.contains("httpAccepted"))
        assertTrue(text.contains("truectrl-settings"))
    }

    @Test
    fun roundTripChangesNothing() {
        val app = AppSettings(poolWarnPercent = 85, hideRecents = true)
        val notify = NotifySettings(intervalMinutes = 30, disabled = setOf(NotifyEvent.UPDATES))
        val plan = SettingsBackups.plan(SettingsBackups.parse(SettingsBackups.export(app, notify, listOf(server), "1.0.0", "now")), app, notify, listOf(server))
        assertTrue(plan.changesNothing)
        assertTrue(plan.unknownServers.isEmpty())
    }

    @Test
    fun importOnlyTightensAndSkipsUnknownServers() {
        val file = SettingsBackup(
            poolWarnPercent = 90, backupMaxAgeDays = 500, hideScreenshots = false, hideRecents = true, notifyIntervalMinutes = 7,
            servers = listOf(
                BackupServer("Renamed", "https://192.168.1.9/", "truectrl", readOnly = false, proxmoxVmId = 103),
                BackupServer("Other", "https://10.0.0.2", "root", readOnly = true),
            ),
        )
        val app = AppSettings(hideScreenshots = true)
        val plan = SettingsBackups.plan(file, app, NotifySettings(), listOf(server.copy(readOnly = true)))
        assertEquals(80 to 90, plan.poolWarnPercent)
        // Out of range, not offered by the app: ignored.
        assertNull(plan.backupMaxAgeDays)
        assertNull(plan.notifyIntervalMinutes)
        assertFalse(plan.hideScreenshotsOn)
        assertTrue(plan.hideRecentsOn)
        assertEquals(listOf("Other"), plan.unknownServers)
        val restore = plan.servers.single()
        assertEquals("Renamed", restore.name)
        assertFalse(restore.readOnlyOn)
        // "Hide in screenshots" stays on although the file says off, and read-only mode stays on.
        assertTrue(SettingsBackups.applyTo(app, plan).hideScreenshots)
        val applied = SettingsBackups.applyTo(server.copy(readOnly = true), restore)
        assertTrue(applied.readOnly)
        assertEquals(103, applied.proxmoxVmId)
        assertEquals("AA:BB", applied.certPin)
        assertEquals("https://192.168.1.9", applied.url)
    }

    @Test
    fun otherFilesAreRefused() {
        for (text in listOf("not json", "{\"format\": \"printctrl-settings\"}")) {
            try {
                SettingsBackups.parse(text)
                fail("accepted: $text")
            } catch (e: BackupFormatException) {
                assertFalse(e.newer)
            }
        }
        try {
            SettingsBackups.parse("{\"format\": \"truectrl-settings\", \"version\": 99}")
            fail("accepted a newer file")
        } catch (e: BackupFormatException) {
            assertTrue(e.newer)
        }
    }
}
