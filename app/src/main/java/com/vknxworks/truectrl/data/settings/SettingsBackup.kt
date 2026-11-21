package com.vknxworks.truectrl.data.settings

import com.vknxworks.truectrl.data.notify.NotifyEvent
import com.vknxworks.truectrl.data.notify.NotifySettings
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.server.Server
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TrueCtrl's settings as a JSON file the user keeps: the warning limits, the two "hide" switches,
 * the notification choices and, per server, its name, read-only mode and Proxmox VM number.
 *
 * Never in the file: API keys, passwords, the ntfy topic and access token, the app PIN, trusted
 * certificate fingerprints and whether plain HTTP was accepted. Also left out on purpose: the app
 * lock and its timeout, and the switches that turn background checks, push and disk health on or
 * off (those need a sign-in, a permission or a set-up step on this phone).
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** TrueCtrl version that wrote the file, for information. */
    val appVersion: String? = null,
    /** ISO time the file was written. */
    val createdAt: String? = null,
    val poolWarnPercent: Int? = null,
    val backupMaxAgeDays: Int? = null,
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    val notifyIntervalMinutes: Int? = null,
    /** Names of the events that are switched off. */
    val notifyDisabled: List<String>? = null,
    val notifyMinAlertLevel: String? = null,
    val diskHealthMinutes: Int? = null,
    val servers: List<BackupServer> = emptyList(),
) {
    companion object {
        const val FORMAT = "truectrl-settings"
        const val VERSION = 1
    }
}

/** A server without anything that lets the app sign in or trust it. */
@Serializable
data class BackupServer(
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val readOnly: Boolean = false,
    val proxmoxVmId: Int? = null,
)

/** What an import would change for one server that's already on this phone. */
data class ServerRestore(
    val id: String,
    val currentName: String,
    /** The new name, when it differs. */
    val name: String?,
    /** Read-only mode gets turned on. An import never turns it off. */
    val readOnlyOn: Boolean,
    /** The Proxmox VM number to set, when the file has one that differs. */
    val proxmoxVmId: Int?,
)

/**
 * Everything an import would change, worked out before anything is touched so it can be shown
 * first. A null field (or false switch) means "stays as it is".
 */
data class RestorePlan(
    val poolWarnPercent: Pair<Int, Int>? = null,
    val backupMaxAgeDays: Pair<Int, Int>? = null,
    /** Only ever turned on by an import. */
    val hideScreenshotsOn: Boolean = false,
    val hideRecentsOn: Boolean = false,
    val notifyIntervalMinutes: Pair<Int, Int>? = null,
    val notifyDisabled: Set<NotifyEvent>? = null,
    val notifyMinAlertLevel: Pair<Severity, Severity>? = null,
    val diskHealthMinutes: Pair<Int, Int>? = null,
    val servers: List<ServerRestore> = emptyList(),
    /** Servers in the file that aren't on this phone: they are not added. */
    val unknownServers: List<String> = emptyList(),
) {
    val changesNothing: Boolean
        get() = poolWarnPercent == null && backupMaxAgeDays == null && !hideScreenshotsOn && !hideRecentsOn &&
            notifyIntervalMinutes == null && notifyDisabled == null && notifyMinAlertLevel == null &&
            diskHealthMinutes == null && servers.isEmpty()
}

/** Thrown for a file that isn't a TrueCtrl settings file, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    /** Settings files are tiny; anything bigger isn't one. */
    const val MAX_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun export(app: AppSettings, notify: NotifySettings, servers: List<Server>, appVersion: String, createdAt: String): String =
        json.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(
                appVersion = appVersion,
                createdAt = createdAt,
                poolWarnPercent = app.poolWarnPercent,
                backupMaxAgeDays = app.backupMaxAgeDays,
                hideScreenshots = app.hideScreenshots,
                hideRecents = app.hideRecents,
                notifyIntervalMinutes = notify.intervalMinutes,
                notifyDisabled = notify.disabled.map { it.name }.sorted(),
                notifyMinAlertLevel = notify.minAlertLevel.name,
                diskHealthMinutes = notify.diskHealthMinutes,
                servers = servers.map { BackupServer(it.name, it.url, it.username, it.readOnly, it.proxmoxVmId) },
            ),
        )

    fun parse(text: String): SettingsBackup {
        val backup = try {
            json.decodeFromString(SettingsBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException(newer = false)
        }
        if (backup.format != SettingsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (backup.version > SettingsBackup.VERSION) throw BackupFormatException(newer = true)
        return backup
    }

    /**
     * Compares the file with what's set now. Values the app wouldn't accept from its own screens
     * (a limit outside its range, an interval that isn't offered, an unknown event) are ignored.
     */
    fun plan(backup: SettingsBackup, app: AppSettings, notify: NotifySettings, servers: List<Server>): RestorePlan {
        fun <T> changed(new: T?, old: T): Pair<T, T>? = if (new != null && new != old) old to new else null

        val disabled = backup.notifyDisabled?.mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }?.toSet()
        val level = backup.notifyMinAlertLevel?.let { n -> Severity.entries.firstOrNull { it.name == n } }
        val known = mutableListOf<ServerRestore>()
        val unknown = mutableListOf<String>()
        for (b in backup.servers) {
            val match = servers.firstOrNull { sameAddress(it.url, b.url) && it.username == b.username }
            if (match == null) {
                unknown += b.name.ifBlank { b.url }
                continue
            }
            val restore = ServerRestore(
                id = match.id,
                currentName = match.name,
                name = b.name.trim().take(MAX_NAME).takeIf { it.isNotBlank() && it != match.name },
                readOnlyOn = b.readOnly && !match.readOnly,
                proxmoxVmId = b.proxmoxVmId?.takeIf { it in 100..999_999_999 && it != match.proxmoxVmId },
            )
            if (restore.name != null || restore.readOnlyOn || restore.proxmoxVmId != null) known += restore
        }
        return RestorePlan(
            poolWarnPercent = changed(backup.poolWarnPercent?.takeIf { it in 50..99 }, app.poolWarnPercent),
            backupMaxAgeDays = changed(backup.backupMaxAgeDays?.takeIf { it in 1..90 }, app.backupMaxAgeDays),
            hideScreenshotsOn = backup.hideScreenshots == true && !app.hideScreenshots,
            hideRecentsOn = backup.hideRecents == true && !app.hideRecents,
            notifyIntervalMinutes = changed(backup.notifyIntervalMinutes?.takeIf { it in NotifySettings.INTERVALS }, notify.intervalMinutes),
            notifyDisabled = disabled?.takeIf { it != notify.disabled },
            notifyMinAlertLevel = changed(level, notify.minAlertLevel),
            diskHealthMinutes = changed(backup.diskHealthMinutes?.takeIf { it in NotifySettings.DISK_INTERVALS }, notify.diskHealthMinutes),
            servers = known,
            unknownServers = unknown,
        )
    }

    fun applyTo(app: AppSettings, plan: RestorePlan): AppSettings = app.copy(
        poolWarnPercent = plan.poolWarnPercent?.second ?: app.poolWarnPercent,
        backupMaxAgeDays = plan.backupMaxAgeDays?.second ?: app.backupMaxAgeDays,
        hideScreenshots = app.hideScreenshots || plan.hideScreenshotsOn,
        hideRecents = app.hideRecents || plan.hideRecentsOn,
    )

    fun applyTo(notify: NotifySettings, plan: RestorePlan): NotifySettings = notify.copy(
        intervalMinutes = plan.notifyIntervalMinutes?.second ?: notify.intervalMinutes,
        disabled = plan.notifyDisabled ?: notify.disabled,
        minAlertLevel = plan.notifyMinAlertLevel?.second ?: notify.minAlertLevel,
        diskHealthMinutes = plan.diskHealthMinutes?.second ?: notify.diskHealthMinutes,
    )

    /** Only what the file may change: never the address, the sign-in, the certificate or the HTTP choice. */
    fun applyTo(server: Server, restore: ServerRestore): Server = server.copy(
        name = restore.name ?: server.name,
        readOnly = server.readOnly || restore.readOnlyOn,
        proxmoxVmId = restore.proxmoxVmId ?: server.proxmoxVmId,
    )

    private fun sameAddress(a: String, b: String): Boolean = a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)

    private const val MAX_NAME = 80
}
