package com.vknxworks.truectrl.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.BuildConfig
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.settings.BackupFormatException
import com.vknxworks.truectrl.data.settings.RestorePlan
import com.vknxworks.truectrl.data.settings.SettingsBackups
import com.vknxworks.truectrl.ui.components.CtrlDialog
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.ShieldScreen
import com.vknxworks.truectrl.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * "Settings file": save TrueCtrl's settings to a file, and read them back from one. An import
 * first shows exactly what it would change and only then, after a confirmation, changes it.
 */
@Composable
fun SettingsFileGroup() {
    val context = LocalContext.current
    val c = context.container
    var plan by remember { mutableStateOf<RestorePlan?>(null) }

    fun toast(text: Int) = Toast.makeText(context, context.getString(text), Toast.LENGTH_LONG).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.appScope.launch {
            val ok = runCatching {
                val text = SettingsBackups.export(
                    c.settings.stored.first(), c.notifySettings.current(), c.serverStore.servers.value,
                    BuildConfig.VERSION_NAME, Instant.now().toString(),
                )
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")).use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            withContext(Dispatchers.Main) { toast(if (ok) R.string.settings_file_exported else R.string.settings_file_export_failed) }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.appScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    (context.contentResolver.openInputStream(uri) ?: error("no stream")).use { input ->
                        // One byte more than allowed, so a file that's too big is noticed without reading it all.
                        val bytes = input.readNBytes(SettingsBackups.MAX_BYTES + 1)
                        if (bytes.size > SettingsBackups.MAX_BYTES) throw BackupFormatException(newer = false)
                        String(bytes, Charsets.UTF_8)
                    }
                }
                SettingsBackups.plan(SettingsBackups.parse(text), c.settings.stored.first(), c.notifySettings.current(), c.serverStore.servers.value)
            }
            withContext(Dispatchers.Main) {
                val read = result.getOrNull()
                val failure = result.exceptionOrNull()
                when {
                    read != null -> plan = read
                    failure is BackupFormatException && failure.newer -> toast(R.string.settings_file_newer)
                    failure is BackupFormatException -> toast(R.string.settings_file_not_one)
                    else -> toast(R.string.settings_file_unreadable)
                }
            }
        }
    }

    Group(stringResource(R.string.settings_file), footer = stringResource(R.string.settings_file_footer)) {
        GroupRow(
            stringResource(R.string.settings_file_export),
            stringResource(R.string.settings_file_export_sub),
            icon = Icons.Outlined.FileUpload,
            onClick = {
                AppLock.expectReturn()
                runCatching { exporter.launch("truectrl-settings-" + LocalDate.now() + ".json") }
            },
        )
        GroupRow(
            stringResource(R.string.settings_file_import),
            stringResource(R.string.settings_file_import_sub),
            icon = Icons.Outlined.FileDownload,
            onClick = {
                AppLock.expectReturn()
                runCatching { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            },
        )
    }

    plan?.let { p ->
        val lines = planLines(p)
        val cancel: @Composable () -> Unit = { TextButton(onClick = { plan = null }) { Text(stringResource(R.string.cancel)) } }
        CtrlDialog(
            onDismissRequest = { plan = null },
            title = { Text(stringResource(if (p.changesNothing) R.string.settings_file_nothing_title else R.string.settings_file_confirm_title)) },
            text = {
                // No other app's floating window can cover the question or trick a tap onto the button.
                ShieldScreen(sensitive = false)
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (p.changesNothing) R.string.settings_file_nothing_body else R.string.settings_file_confirm_body))
                    lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                    if (p.unknownServers.isNotEmpty()) {
                        Text(stringResource(R.string.settings_file_unknown_servers, p.unknownServers.joinToString()), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.settings_file_never), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (p.changesNothing) {
                    TextButton(onClick = { plan = null }) { Text(stringResource(R.string.close)) }
                } else {
                    TextButton(onClick = {
                        plan = null
                        c.appScope.launch {
                            val ok = runCatching {
                                c.settings.update { SettingsBackups.applyTo(it, p) }
                                c.notifySettings.update { SettingsBackups.applyTo(it, p) }
                                p.servers.forEach { r -> c.serverStore.update(r.id) { SettingsBackups.applyTo(it, r) } }
                            }.isSuccess
                            withContext(Dispatchers.Main) { toast(if (ok) R.string.settings_file_imported else R.string.settings_file_import_failed) }
                        }
                    }) { Text(stringResource(R.string.settings_file_replace)) }
                }
            },
            dismissButton = if (p.changesNothing) null else cancel,
        )
    }
}

@Composable
private fun severity(s: Severity): String = stringResource(
    when (s) {
        Severity.CRITICAL -> R.string.sev_critical
        Severity.WARNING -> R.string.sev_warning
        Severity.INFO -> R.string.sev_info
    },
)

/** Every change of [p] as a line: what it is, the value now and the value after. */
@Composable
private fun planLines(p: RestorePlan): List<String> = buildList {
    p.poolWarnPercent?.let { add(stringResource(R.string.settings_file_pool_warn, it.first, it.second)) }
    p.backupMaxAgeDays?.let { add(stringResource(R.string.settings_file_backup_age, it.first, pluralStringResource(R.plurals.backup_age_value, it.second, it.second))) }
    if (p.hideScreenshotsOn) add(stringResource(R.string.settings_file_turn_on, stringResource(R.string.hide_screenshots)))
    if (p.hideRecentsOn) add(stringResource(R.string.settings_file_turn_on, stringResource(R.string.hide_recents)))
    p.notifyIntervalMinutes?.let { add(stringResource(R.string.settings_file_interval, it.first, it.second)) }
    p.diskHealthMinutes?.let { add(stringResource(R.string.settings_file_disk_interval, it.first, it.second)) }
    p.notifyMinAlertLevel?.let { add(stringResource(R.string.settings_file_min_level, severity(it.first), severity(it.second))) }
    p.notifyDisabled?.let { off ->
        add(
            if (off.isEmpty()) stringResource(R.string.settings_file_events_all)
            else stringResource(R.string.settings_file_events, off.map { stringResource(it.label) }.joinToString()),
        )
    }
    p.servers.forEach { s ->
        s.name?.let { add(stringResource(R.string.settings_file_server_name, s.currentName, it)) }
        if (s.readOnlyOn) add(stringResource(R.string.settings_file_server_read_only, s.currentName))
        s.proxmoxVmId?.let { add(stringResource(R.string.settings_file_server_vm, s.currentName, it)) }
    }
}
