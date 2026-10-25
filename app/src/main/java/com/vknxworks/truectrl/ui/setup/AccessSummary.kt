package com.vknxworks.truectrl.ui.setup

import androidx.compose.material.icons.outlined.CheckCircle
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.theme.Health

/** One thing the app can do, the TrueNAS roles it needs (all of them), and how they're shown. */
private class Capability(@StringRes val label: Int, val roles: List<String>, val shown: String = roles.joinToString(" + "))

/**
 * The roles the app looks for (see the README). Deleting datasets and snapshots needs its own
 * *_DELETE role, which the matching *_WRITE role doesn't include. Reboot and shutdown are Full Admin only.
 */
private val capabilities = listOf(
    Capability(R.string.cap_monitor, listOf("READONLY_ADMIN")),
    Capability(R.string.cap_datasets, listOf("DATASET_WRITE")),
    Capability(R.string.cap_dataset_delete, listOf("DATASET_DELETE")),
    Capability(R.string.cap_snapshots, listOf("SNAPSHOT_WRITE")),
    Capability(R.string.cap_snapshot_delete, listOf("SNAPSHOT_DELETE")),
    Capability(R.string.cap_scrubs, listOf("POOL_WRITE")),
    Capability(R.string.cap_replication, listOf("REPLICATION_TASK_WRITE")),
    Capability(R.string.cap_cloud_sync, listOf("CLOUD_SYNC_WRITE")),
    Capability(R.string.cap_shares, listOf("SHARING_SMB_WRITE", "SHARING_NFS_WRITE")),
    Capability(R.string.cap_services, listOf("SERVICE_READ", "SERVICE_WRITE")),
    Capability(R.string.cap_apps, listOf("APPS_WRITE")),
    Capability(R.string.cap_alerts, listOf("ALERT_LIST_WRITE")),
    Capability(R.string.cap_updates, listOf("SYSTEM_UPDATE_WRITE")),
    Capability(R.string.cap_power, listOf(Capabilities.FULL_ADMIN), shown = "Full Admin"),
)

/** Lists what the signed-in user is allowed to do, with the roles behind each item. */
@Composable
fun AccessSummary(caps: Capabilities) {
    val footer = if (caps.isEmpty) stringResource(R.string.access_none_warning) else stringResource(R.string.access_footer)
    Group(stringResource(R.string.access_title), footer = footer) {
        capabilities.forEach { cap ->
            val ok = cap.roles.all { caps.hasRole(it) }
            AccessRow(stringResource(cap.label), cap.shown, ok)
        }
        AccessRow(stringResource(R.string.cap_shell), stringResource(R.string.cap_shell_privilege), caps.webShell || caps.isFullAdmin)
    }
}

@Composable
private fun AccessRow(title: String, needs: String, ok: Boolean) {
    GroupRow(
        title = title,
        subtitle = "${stringResource(if (ok) R.string.allowed else R.string.not_allowed)}  ·  $needs",
        titleColor = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            if (ok) Icon(Icons.Outlined.CheckCircle, null, tint = Health.ok)
            else Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}
