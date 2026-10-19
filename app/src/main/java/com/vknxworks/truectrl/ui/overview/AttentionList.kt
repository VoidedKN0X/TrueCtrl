package com.vknxworks.truectrl.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.overview.AttentionItem
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.overview.TaskKind
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.components.percent
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health

/** Where tapping an attention item goes. */
enum class AttentionTarget { ALERTS, STORAGE, PROTECTION, SHARING, SYSTEM }

private const val COLLAPSED_COUNT = 5

fun AttentionItem.target(): AttentionTarget = when (this) {
    is AttentionItem.Alert, is AttentionItem.MoreAlerts -> AttentionTarget.ALERTS
    is AttentionItem.PoolUnhealthy, is AttentionItem.PoolFull, is AttentionItem.ScrubErrors -> AttentionTarget.STORAGE
    is AttentionItem.ScrubOverdue, is AttentionItem.TaskFailed, is AttentionItem.BackupStale -> AttentionTarget.PROTECTION
    is AttentionItem.ServiceStopped -> AttentionTarget.SHARING
    is AttentionItem.AppCrashed, is AttentionItem.UpdateAvailable, is AttentionItem.RebootPending -> AttentionTarget.SYSTEM
}

/** TrueNAS service names as people know them. */
fun serviceLabel(service: String): String = when (service.lowercase()) {
    "cifs" -> "SMB"
    "nfs" -> "NFS"
    "ssh" -> "SSH"
    "ups" -> "UPS"
    "snmp" -> "SNMP"
    "ftp" -> "FTP"
    "iscsitarget" -> "iSCSI"
    "nvmet" -> "NVMe-oF"
    "smartd" -> "S.M.A.R.T."
    else -> service.replaceFirstChar { it.uppercase() }
}

@Composable
private fun taskKind(kind: TaskKind): String = stringResource(
    when (kind) {
        TaskKind.REPLICATION -> R.string.task_replication
        TaskKind.SNAPSHOT -> R.string.task_snapshot
        TaskKind.CLOUD_SYNC -> R.string.task_cloud_sync
        TaskKind.RSYNC -> R.string.task_rsync
    },
)

@Composable
private fun title(item: AttentionItem): String = when (item) {
    is AttentionItem.Alert -> alertText(item.alert.text).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    is AttentionItem.MoreAlerts -> pluralStringResource(R.plurals.att_more_alerts, item.count, item.count)
    is AttentionItem.PoolUnhealthy -> stringResource(R.string.att_pool_unhealthy, item.pool.name, item.pool.status)
    is AttentionItem.PoolFull -> stringResource(R.string.att_pool_full, item.pool.name, percent(item.fraction))
    is AttentionItem.ScrubErrors -> pluralStringResource(R.plurals.att_scrub_errors, item.errors.toInt(), item.pool.name, item.errors.toInt())
    is AttentionItem.ScrubOverdue -> stringResource(R.string.att_scrub_overdue, item.pool.name)
    is AttentionItem.TaskFailed -> stringResource(R.string.att_task_failed, taskKind(item.task.kind), item.task.name)
    is AttentionItem.BackupStale -> stringResource(R.string.att_backup_stale, taskKind(item.task.kind), item.task.name)
    is AttentionItem.ServiceStopped -> stringResource(R.string.att_service_stopped, serviceLabel(item.service.service))
    is AttentionItem.AppCrashed -> stringResource(R.string.att_app_crashed, item.app.name)
    is AttentionItem.UpdateAvailable -> item.version?.let { stringResource(R.string.att_update_version, it) } ?: stringResource(R.string.att_update)
    is AttentionItem.RebootPending -> stringResource(R.string.att_reboot)
}

@Composable
private fun detail(item: AttentionItem): String? = when (item) {
    is AttentionItem.Alert -> listOfNotNull(
        alertText(item.alert.text).lines().drop(1).joinToString(" ").trim().takeIf { it.isNotBlank() },
        item.alert.atMs?.let { relativeTime(it) },
    ).joinToString("  ·  ").takeIf { it.isNotBlank() }
    is AttentionItem.MoreAlerts -> null
    is AttentionItem.PoolUnhealthy -> item.pool.statusDetail ?: stringResource(R.string.att_pool_unhealthy_detail)
    is AttentionItem.PoolFull -> stringResource(R.string.att_pool_full_detail, formatBytes(item.pool.allocated), formatBytes(item.pool.size))
    is AttentionItem.ScrubErrors -> stringResource(R.string.att_scrub_errors_detail)
    is AttentionItem.ScrubOverdue -> item.lastMs?.let { stringResource(R.string.att_scrub_last, relativeTime(it)) } ?: stringResource(R.string.att_scrub_never)
    is AttentionItem.TaskFailed -> listOfNotNull(item.task.error, item.task.atMs?.let { relativeTime(it) }).joinToString("  ·  ").takeIf { it.isNotBlank() }
    is AttentionItem.BackupStale -> item.task.atMs?.let { stringResource(R.string.att_backup_stale_detail, relativeTime(it), item.maxAgeDays) }
    is AttentionItem.ServiceStopped -> stringResource(R.string.att_service_stopped_detail)
    is AttentionItem.AppCrashed -> stringResource(R.string.att_app_crashed_detail)
    is AttentionItem.UpdateAvailable -> stringResource(R.string.att_update_detail)
    is AttentionItem.RebootPending -> item.reasons.joinToString("\n").ifBlank { stringResource(R.string.att_reboot_detail) }
}

/** Severity icons differ in shape as well as colour, and carry a label for screen readers. */
@Composable
fun SeverityIcon(severity: Severity) {
    val (icon, tint, label) = when (severity) {
        Severity.CRITICAL -> Triple(Icons.Filled.Error, Health.critical, R.string.sev_critical)
        Severity.WARNING -> Triple(Icons.Filled.Warning, Health.warn, R.string.sev_warning)
        Severity.INFO -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, R.string.sev_info)
    }
    Icon(icon, stringResource(label), Modifier.size(22.dp), tint = tint)
}

/** The attention list at the top of the Overview. Critical first; collapsed to five with "Show all". */
@Composable
fun AttentionCard(items: List<AttentionItem>, serverName: String, onOpen: (AttentionTarget) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        Text(
            stringResource(R.string.attention_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            if (items.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(22.dp), tint = Health.ok)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.attention_all_good), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.attention_all_good_body, serverName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(Modifier.padding(vertical = 4.dp)) {
                    val shown = if (expanded) items else items.take(COLLAPSED_COUNT)
                    shown.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        AttentionRow(item, onOpen)
                    }
                    if (items.size > COLLAPSED_COUNT) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 40.dp)) {
                            Text(if (expanded) stringResource(R.string.attention_show_less) else stringResource(R.string.attention_show_all, items.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, onOpen: (AttentionTarget) -> Unit) {
    val target = item.target()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(target) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SeverityIcon(item.severity)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title(item), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail(item)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
