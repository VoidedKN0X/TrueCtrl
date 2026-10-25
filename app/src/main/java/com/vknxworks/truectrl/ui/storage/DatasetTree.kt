package com.vknxworks.truectrl.ui.storage

import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.storage.DatasetInfo
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.theme.Health

/** Indentation per level in the dataset tree; deep trees stop indenting so names stay readable. */
private const val MAX_INDENT_LEVELS = 6

/** One dataset in the tree: indented by depth, with its icon, space used and lock state. */
@Composable
fun DatasetTreeRow(d: DatasetInfo, level: Int = d.depth, onClick: () -> Unit) {
    val indent = (minOf(level, MAX_INDENT_LEVELS) * 16).dp
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp + indent, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint) = when {
            d.locked -> Icons.Outlined.Lock to Health.warn
            d.isRoot -> Icons.Outlined.Storage to MaterialTheme.colorScheme.primary
            d.isZvol -> Icons.Outlined.SdStorage to MaterialTheme.colorScheme.primary
            else -> Icons.Outlined.Folder to MaterialTheme.colorScheme.primary
        }
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                datasetSummary(d),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (d.encrypted && !d.locked) {
            Icon(Icons.Outlined.LockOpen, stringResource(R.string.dataset_unlocked), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "12.3 GiB used · 1.2 TiB free · quota 500 GiB", or "Locked". */
@Composable
fun datasetSummary(d: DatasetInfo): String {
    if (d.locked) return stringResource(R.string.dataset_locked)
    return listOfNotNull(
        if (d.isZvol) d.volsize?.let { stringResource(R.string.zvol_size, formatBytes(it)) } else null,
        d.used?.let { stringResource(R.string.dataset_used, formatBytes(it)) },
        d.available?.let { stringResource(R.string.dataset_free, formatBytes(it)) },
        d.quota.takeIf { it > 0 }?.let { stringResource(R.string.dataset_quota_short, formatBytes(it)) },
    ).joinToString("  ·  ")
}
