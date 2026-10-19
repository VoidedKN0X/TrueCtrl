package com.vknxworks.truectrl.ui.protection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.protection.SnapshotAction
import com.vknxworks.truectrl.data.protection.SnapshotInfo
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.storage.DatasetTreeRow
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Something done to one snapshot, waiting for confirmation. */
private sealed interface SnapPending {
    data class Rollback(val snap: SnapshotInfo, val newer: Int) : SnapPending
    data class Clone(val snap: SnapshotInfo) : SnapPending
    data class Delete(val ids: List<String>) : SnapPending
    data object Create : SnapPending
}

/** ZFS dataset and snapshot names: letters, digits and - _ . : (and / between datasets). */
private val SNAP_NAME = Regex("^[A-Za-z0-9_.:-]+$")
private val DATASET_PATH = Regex("^[A-Za-z0-9_.:-]+(/[A-Za-z0-9_.:-]+)+$")

/**
 * Snapshots of one dataset, newest first: take one, delete one or several, roll back, clone.
 * Without a dataset it first asks which one.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SnapshotsScreen(initialDataset: String?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val datasetsState by c.datasets.state.collectAsStateWithLifecycle()
    val server = active?.server
    var dataset by rememberSaveable { mutableStateOf(initialDataset) }
    var snaps by remember { mutableStateOf<List<SnapshotInfo>?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sheetFor by remember { mutableStateOf<SnapshotInfo?>(null) }
    var pending by remember { mutableStateOf<SnapPending?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val ns = access.capabilities?.snapshotNamespace
    val canCreate = c.protection.canSnapshot(SnapshotAction.CREATE)
    val canDelete = c.protection.canSnapshot(SnapshotAction.DELETE)
    val canRollback = c.protection.canSnapshot(SnapshotAction.ROLLBACK)
    val canClone = c.protection.canSnapshot(SnapshotAction.CLONE)

    suspend fun load() {
        val d = dataset ?: return
        try {
            snaps = c.protection.snapshots(d)
            loadError = null
            selected = selected.intersect(snaps.orEmpty().map { it.id }.toSet())
        } catch (e: TnError) {
            loadError = e
        }
    }

    LaunchedEffect(dataset, server?.id, access.generation, access.connected) {
        if (dataset == null) {
            if (datasetsState.updatedAt == 0L || datasetsState.error != null) c.datasets.refresh()
        } else if (access.connected) {
            load()
        }
    }

    fun act(done: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
                selected = emptySet()
                load()
                snackbar.showSnackbar(done)
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val d = dataset
        if (selected.isNotEmpty()) {
            ScreenTopBar(
                pluralStringResource(R.plurals.snapshots_selected, selected.size, selected.size),
                actions = {
                    IconButton(onClick = { pending = SnapPending.Delete(selected.toList()) }, enabled = !busy) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                    }
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                },
            )
        } else {
            ScreenTopBar(
                d ?: stringResource(R.string.snapshots_title),
                actions = {
                    if (d != null && canCreate) {
                        IconButton(onClick = { pending = SnapPending.Create }, enabled = !busy) {
                            Icon(Icons.Filled.Add, stringResource(R.string.snapshot_create))
                        }
                    }
                },
            )
        }
        if (!access.connected && access.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    if (dataset == null) c.datasets.refresh() else load()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            when {
                ns == null && access.capabilities != null -> EmptyState(
                    Icons.Outlined.CameraAlt,
                    stringResource(R.string.snapshots_no_access),
                    stringResource(R.string.snapshots_no_access_body),
                )
                d == null -> {
                    val list = datasetsState.datasets
                    if (list.isEmpty()) {
                        EmptyState(
                            Icons.Outlined.CameraAlt,
                            stringResource(if (datasetsState.loading) R.string.overview_loading else R.string.datasets_none),
                            stringResource(R.string.snapshots_pick_body),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(vertical = 8.dp)) {
                            item {
                                Text(
                                    stringResource(R.string.snapshots_pick),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                                )
                            }
                            items(list, key = { it.id }) { ds ->
                                DatasetTreeRow(ds) {
                                    dataset = ds.id
                                    snaps = null
                                }
                            }
                        }
                    }
                }
                loadError != null && snaps == null -> EmptyState(
                    Icons.Outlined.CameraAlt,
                    stringResource(R.string.snapshots_failed),
                    errorMessage(context, loadError!!, server?.host.orEmpty()),
                )
                snaps == null -> EmptyState(Icons.Outlined.CameraAlt, stringResource(R.string.overview_loading), d.orEmpty())
                snaps!!.isEmpty() -> EmptyState(
                    Icons.Outlined.CameraAlt,
                    stringResource(R.string.snapshots_none),
                    stringResource(R.string.snapshots_none_body, d.orEmpty()),
                )
                else -> {
                    val list = snaps!!
                    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item(key = "count") {
                            Text(
                                pluralStringResource(R.plurals.snapshot_count, list.size, list.size) +
                                    if (canDelete) "  ·  " + stringResource(R.string.snapshots_hold_hint) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                            )
                        }
                        item(key = "list") {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                            ) {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    list.forEachIndexed { i, s ->
                                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                        val isSel = s.id in selected
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = {
                                                        when {
                                                            selected.isNotEmpty() && !s.holds -> selected = if (isSel) selected - s.id else selected + s.id
                                                            selected.isEmpty() -> sheetFor = s
                                                        }
                                                    },
                                                    onLongClick = { if (canDelete && !s.holds) selected = selected + s.id },
                                                )
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (selected.isNotEmpty()) {
                                                Checkbox(checked = isSel, onCheckedChange = null, enabled = !s.holds)
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(s.name, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    listOfNotNull(
                                                        s.createdMs?.let { relativeTime(it) },
                                                        s.used?.let { stringResource(R.string.snapshot_used, formatBytes(it)) },
                                                        s.referenced?.let { stringResource(R.string.snapshot_referenced, formatBytes(it)) },
                                                    ).joinToString("  ·  "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (s.holds) Icon(Icons.Filled.Lock, stringResource(R.string.snapshot_held), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            else if (selected.isEmpty()) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    sheetFor?.let { s ->
        val list = snaps.orEmpty()
        val newer = list.count { (it.createdMs ?: 0) > (s.createdMs ?: 0) }
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    listOfNotNull(
                        s.createdMs?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
                        if (s.holds) stringResource(R.string.snapshot_held) else null,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                SheetAction(Icons.Outlined.ContentCopy, stringResource(R.string.snapshot_copy_name), true) {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText(s.id, s.id))
                    sheetFor = null
                }
                SheetAction(Icons.Outlined.History, stringResource(R.string.snapshot_rollback), canRollback && !busy) {
                    sheetFor = null
                    pending = SnapPending.Rollback(s, newer)
                }
                SheetAction(Icons.Outlined.FileCopy, stringResource(R.string.snapshot_clone), canClone && !busy) {
                    sheetFor = null
                    pending = SnapPending.Clone(s)
                }
                SheetAction(Icons.Outlined.Delete, stringResource(R.string.delete), canDelete && !s.holds && !busy, destructive = true) {
                    sheetFor = null
                    pending = SnapPending.Delete(listOf(s.id))
                }
                if (!canRollback || !canClone || !canDelete) {
                    Text(
                        stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.snapshots_role_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    when (val p = pending) {
        null -> Unit
        is SnapPending.Create -> CreateSnapshotDialog(
            dataset = dataset.orEmpty(),
            onDismiss = { pending = null },
            onCreate = { name, recursive ->
                pending = null
                val ds = dataset ?: return@CreateSnapshotDialog
                act(context.getString(R.string.snapshot_created)) { c.protection.createSnapshot(ds, name, recursive) }
            },
        )
        is SnapPending.Rollback -> ConfirmDialog(
            title = stringResource(R.string.rollback_title, p.snap.name),
            body = stringResource(R.string.rollback_body, p.snap.dataset) +
                if (p.newer > 0) "\n\n" + pluralStringResource(R.plurals.rollback_newer, p.newer, p.newer) else "",
            confirmLabel = stringResource(R.string.snapshot_rollback),
            destructive = true,
            typeToConfirm = p.snap.dataset,
            onConfirm = {
                pending = null
                act(context.getString(R.string.rollback_done)) { c.protection.rollback(p.snap.id, destroyNewer = p.newer > 0) }
            },
            onDismiss = { pending = null },
        )
        is SnapPending.Clone -> CloneDialog(
            snap = p.snap,
            onDismiss = { pending = null },
            onClone = { target ->
                pending = null
                act(context.getString(R.string.clone_done, target)) {
                    c.protection.clone(p.snap.id, target)
                    c.datasets.refresh()
                }
            },
        )
        is SnapPending.Delete -> ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_snapshots_title, p.ids.size, p.ids.size),
            body = if (p.ids.size == 1) {
                stringResource(R.string.delete_snapshot_body, p.ids.first())
            } else {
                pluralStringResource(R.plurals.delete_snapshots_body, p.ids.size, p.ids.size, dataset.orEmpty())
            },
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            // Many at once is easy to get wrong: type the dataset's name.
            typeToConfirm = if (p.ids.size >= 5) dataset else null,
            onConfirm = {
                pending = null
                act(context.resources.getQuantityString(R.plurals.snapshots_deleted, p.ids.size, p.ids.size)) { c.protection.deleteSnapshots(p.ids) }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, destructive: Boolean = false, onClick: () -> Unit) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(16.dp))
            Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CreateSnapshotDialog(dataset: String, onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("manual-" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())) }
    var recursive by rememberSaveable { mutableStateOf(false) }
    val valid = SNAP_NAME.matches(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snapshot_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.snapshot_create_body, dataset))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    label = { Text(stringResource(R.string.snapshot_name)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) ({ Text(stringResource(R.string.name_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recursive, onCheckedChange = { recursive = it })
                    Text(stringResource(R.string.snapshot_recursive), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, recursive) }, enabled = valid) { Text(stringResource(R.string.snapshot_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CloneDialog(snap: SnapshotInfo, onDismiss: () -> Unit, onClone: (String) -> Unit) {
    var target by rememberSaveable { mutableStateOf(snap.dataset + "-clone") }
    val valid = DATASET_PATH.matches(target) && target.substringBefore('/') == snap.dataset.substringBefore('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clone_title, snap.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.clone_body))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.trim() },
                    label = { Text(stringResource(R.string.clone_target)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) ({ Text(stringResource(R.string.clone_target_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onClone(target) }, enabled = valid) { Text(stringResource(R.string.snapshot_clone)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
