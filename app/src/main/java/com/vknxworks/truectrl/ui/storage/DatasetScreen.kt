package com.vknxworks.truectrl.ui.storage

import com.vknxworks.truectrl.ui.components.CtrlDialog
import com.vknxworks.truectrl.ui.components.CtrlTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.storage.DatasetInfo
import com.vknxworks.truectrl.data.storage.DatasetPreset
import com.vknxworks.truectrl.data.storage.DeletePreview
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.SecureWindow
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.nav.DatasetRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.SnapshotsRoute
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private const val GIB = 1024L * 1024 * 1024
private val NAME = Regex("^[A-Za-z0-9_.:-]+$")

private enum class DatasetDialog { EDIT, CREATE, LOCK, UNLOCK, DELETE }

/** One dataset or zvol: details, children, snapshots, and editing, locking and deleting it. */
@Composable
fun DatasetScreen(id: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.datasets.state.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val caps = access.capabilities
    val d = state.datasets.firstOrNull { it.id == id }
    val children = state.datasets.filter { it.parent == id }
    var dialog by remember { mutableStateOf<DatasetDialog?>(null) }
    var preview by remember { mutableStateOf<DeletePreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(server?.id, access.generation) { if (access.connected) c.datasets.refresh() }

    val canEdit = caps?.has("pool.dataset.update") == true && c.sessions.canWrite("DATASET_WRITE")
    val canCreate = caps?.has("pool.dataset.create") == true && c.sessions.canWrite("DATASET_WRITE")
    val canDelete = caps?.has("pool.dataset.delete") == true && c.sessions.canWrite("DATASET_DELETE")
    val canLock = caps?.has("pool.dataset.lock") == true && c.sessions.canWrite("DATASET_WRITE")
    val canUnlock = caps?.has("pool.dataset.unlock") == true && c.sessions.canWrite("DATASET_WRITE")
    val canSnapshots = caps?.snapshotNamespace != null

    fun act(done: String, after: () -> Unit = {}, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
                after()
                snackbar.showSnackbar(done)
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(d?.name ?: id.substringAfterLast('/'))
        if (!access.connected && access.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    c.datasets.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (d == null) {
                EmptyState(
                    Icons.Outlined.Folder,
                    stringResource(if (state.loading) R.string.overview_loading else R.string.dataset_missing),
                    stringResource(R.string.dataset_missing_body, id),
                    Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    Details(d)
                    if (children.isNotEmpty()) {
                        Group(stringResource(R.string.dataset_children)) {
                            children.forEach { ch -> DatasetTreeRow(ch, level = 0) { nav.navigate(DatasetRoute(ch.id)) } }
                        }
                    }
                    Group(stringResource(R.string.actions)) {
                        if (canSnapshots && !d.locked) {
                            GroupRow(stringResource(R.string.snapshots_title), stringResource(R.string.dataset_snapshots_sub), Icons.Outlined.CameraAlt, showChevron = true, onClick = {
                                nav.navigate(SnapshotsRoute(d.id))
                            })
                        }
                        if (canEdit && !d.locked) {
                            GroupRow(stringResource(R.string.dataset_edit), stringResource(R.string.dataset_edit_sub), Icons.Outlined.Edit, onClick = { dialog = DatasetDialog.EDIT })
                        }
                        if (canCreate && !d.isZvol && !d.locked) {
                            GroupRow(stringResource(R.string.dataset_add_child), stringResource(R.string.dataset_add_child_sub), Icons.Outlined.Add, onClick = { dialog = DatasetDialog.CREATE })
                        }
                        if (d.isEncryptionRoot && !d.locked && canLock && !d.isRoot) {
                            GroupRow(stringResource(R.string.dataset_lock), stringResource(R.string.dataset_lock_sub), Icons.Outlined.Lock, onClick = { dialog = DatasetDialog.LOCK })
                        }
                        if (d.locked && canUnlock) {
                            val passphrase = d.keyFormat.equals("PASSPHRASE", ignoreCase = true)
                            GroupRow(
                                stringResource(R.string.dataset_unlock),
                                stringResource(if (passphrase) R.string.dataset_unlock_sub else R.string.dataset_unlock_key_sub),
                                Icons.Outlined.LockOpen,
                                onClick = if (passphrase) ({ dialog = DatasetDialog.UNLOCK }) else null,
                            )
                        }
                        if (canDelete && !d.isRoot) {
                            GroupRow(
                                stringResource(R.string.dataset_delete),
                                stringResource(R.string.dataset_delete_sub),
                                Icons.Outlined.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                preview = c.datasets.deletePreview(d)
                                                dialog = DatasetDialog.DELETE
                                            } catch (e: Exception) {
                                                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                },
                                trailing = if (busy && dialog == null) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                            )
                        }
                        if (!canEdit && !canDelete && !canCreate) {
                            Text(
                                stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.dataset_role_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    if (d == null) return
    when (dialog) {
        null -> Unit
        DatasetDialog.EDIT -> EditDialog(
            d,
            onDismiss = { dialog = null },
            onSave = { quota, comments ->
                dialog = null
                act(context.getString(R.string.dataset_saved)) { c.datasets.update(d.id, quota, comments) }
            },
        )
        DatasetDialog.CREATE -> CreateDialog(
            d,
            existing = children.map { it.name }.toSet(),
            onDismiss = { dialog = null },
            onCreate = { name, preset, comments ->
                dialog = null
                act(context.getString(R.string.dataset_created, name)) { c.datasets.create(d.id + "/" + name, preset, comments) }
            },
        )
        DatasetDialog.LOCK -> ConfirmDialog(
            title = stringResource(R.string.dataset_lock_title, d.id),
            body = stringResource(R.string.dataset_lock_body),
            confirmLabel = stringResource(R.string.dataset_lock),
            destructive = true,
            typeToConfirm = d.id,
            onConfirm = {
                dialog = null
                act(context.getString(R.string.dataset_locked_done)) { c.datasets.lock(d.id) }
            },
            onDismiss = { dialog = null },
        )
        DatasetDialog.UNLOCK -> UnlockDialog(
            d,
            onDismiss = { dialog = null },
            onUnlock = { pass ->
                dialog = null
                act(context.getString(R.string.dataset_unlocked_done)) { c.datasets.unlock(d.id, pass) }
            },
        )
        DatasetDialog.DELETE -> {
            val p = preview
            ConfirmDialog(
                title = stringResource(R.string.dataset_delete_title, d.name),
                body = deleteBody(d, p),
                confirmLabel = stringResource(R.string.delete),
                destructive = true,
                typeToConfirm = d.id,
                onConfirm = {
                    dialog = null
                    act(context.getString(R.string.dataset_deleted, d.id), after = { nav.back() }) { c.datasets.delete(d.id) }
                },
                onDismiss = {
                    dialog = null
                    preview = null
                },
            )
        }
    }
}

@Composable
private fun deleteBody(d: DatasetInfo, p: DeletePreview?): String {
    val lines = mutableListOf(stringResource(R.string.dataset_delete_body, d.id))
    if (p != null) {
        if (p.children.isNotEmpty()) lines += stringResource(R.string.dataset_delete_children, p.children.size, p.children.take(5).joinToString())
        p.snapshots?.takeIf { it > 0 }?.let { lines += stringResource(R.string.dataset_delete_snapshots, it) }
        if (p.attachments.isNotEmpty()) lines += stringResource(R.string.dataset_delete_attachments, p.attachments.joinToString())
        if (p.processes.isNotEmpty()) lines += stringResource(R.string.dataset_delete_processes, p.processes.joinToString())
    }
    return lines.joinToString("\n\n")
}

@Composable
private fun Details(d: DatasetInfo) {
    Group(stringResource(R.string.details)) {
        GroupRow(stringResource(R.string.dataset_path), d.id)
        GroupRow(
            stringResource(R.string.dataset_type),
            stringResource(if (d.isZvol) R.string.dataset_type_zvol else R.string.dataset_type_fs),
        )
        if (d.locked) {
            GroupRow(stringResource(R.string.dataset_encryption), stringResource(R.string.dataset_locked), titleColor = Health.warn)
        } else {
            d.used?.let { GroupRow(stringResource(R.string.dataset_used_title), formatBytes(it)) }
            d.available?.let { GroupRow(stringResource(R.string.dataset_free_title), formatBytes(it)) }
            if (d.isZvol) d.volsize?.let { GroupRow(stringResource(R.string.zvol_size_title), formatBytes(it)) }
            GroupRow(stringResource(R.string.dataset_quota), if (d.quota > 0) formatBytes(d.quota) else stringResource(R.string.none))
            if (d.reservation > 0) GroupRow(stringResource(R.string.dataset_reservation), formatBytes(d.reservation))
            d.compression?.let { comp ->
                GroupRow(stringResource(R.string.dataset_compression), listOfNotNull(comp, d.compressRatio?.let { "${it}×".replace("x×", "×") }).joinToString("  ·  "))
            }
            d.recordsize?.takeIf { !d.isZvol }?.let { GroupRow(stringResource(R.string.dataset_recordsize), it) }
            d.mountpoint?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.dataset_mountpoint), it) }
            if (d.encrypted) {
                GroupRow(
                    stringResource(R.string.dataset_encryption),
                    if (d.isEncryptionRoot) stringResource(R.string.dataset_encryption_root) else stringResource(R.string.dataset_encryption_inherited, d.encryptionRoot.orEmpty()),
                )
            }
            if (d.readonly) GroupRow(stringResource(R.string.dataset_readonly), stringResource(R.string.yes))
        }
        if (d.comments.isNotBlank()) GroupRow(stringResource(R.string.dataset_comments), d.comments)
    }
}

@Composable
private fun EditDialog(d: DatasetInfo, onDismiss: () -> Unit, onSave: (Long?, String?) -> Unit) {
    val startQuota = if (d.quota > 0) trimNumber(d.quota.toDouble() / GIB) else ""
    var quota by rememberSaveable { mutableStateOf(startQuota) }
    var comments by rememberSaveable { mutableStateOf(d.comments) }
    val gib = quota.replace(',', '.').toDoubleOrNull()
    val quotaOk = quota.isBlank() || (gib != null && (gib == 0.0 || gib >= 1.0))
    CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dataset_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!d.isZvol) {
                    CtrlTextField(
                        value = quota,
                        onValueChange = { quota = it.trim() },
                        label = { Text(stringResource(R.string.dataset_quota_gib)) },
                        supportingText = { Text(stringResource(if (quotaOk) R.string.dataset_quota_hint else R.string.dataset_quota_invalid)) },
                        isError = !quotaOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CtrlTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text(stringResource(R.string.dataset_comments)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = quotaOk,
                onClick = {
                    val newQuota = when {
                        d.isZvol || quota == startQuota -> null
                        gib == null -> 0L
                        else -> (gib * GIB).roundToLong()
                    }
                    onSave(newQuota, comments.takeIf { it != d.comments })
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun trimNumber(v: Double): String = if (v == v.roundToLong().toDouble()) v.roundToLong().toString() else "%.2f".format(java.util.Locale.ROOT, v)

@Composable
private fun CreateDialog(parent: DatasetInfo, existing: Set<String>, onDismiss: () -> Unit, onCreate: (String, DatasetPreset, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var preset by rememberSaveable { mutableStateOf(DatasetPreset.GENERIC) }
    var comments by rememberSaveable { mutableStateOf("") }
    val valid = NAME.matches(name) && name !in existing
    CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dataset_add_child)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dataset_create_in, parent.id), style = MaterialTheme.typography.bodyMedium)
                CtrlTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    label = { Text(stringResource(R.string.dataset_name)) },
                    singleLine = true,
                    isError = name.isNotEmpty() && !valid,
                    supportingText = if (name.isNotEmpty() && !valid) ({ Text(stringResource(if (name in existing) R.string.dataset_name_taken else R.string.name_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.dataset_preset), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatasetPreset.entries.forEach { p ->
                        FilterChip(selected = preset == p, onClick = { preset = p }, label = { Text(presetLabel(p)) })
                    }
                }
                Text(presetHint(preset), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CtrlTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text(stringResource(R.string.dataset_comments)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, preset, comments) }, enabled = valid) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun presetLabel(p: DatasetPreset): String = stringResource(
    when (p) {
        DatasetPreset.GENERIC -> R.string.preset_generic
        DatasetPreset.SMB -> R.string.preset_smb
        DatasetPreset.NFS -> R.string.preset_nfs
        DatasetPreset.MULTIPROTOCOL -> R.string.preset_multi
        DatasetPreset.APPS -> R.string.preset_apps
    },
)

@Composable
private fun presetHint(p: DatasetPreset): String = stringResource(
    when (p) {
        DatasetPreset.GENERIC -> R.string.preset_generic_hint
        DatasetPreset.SMB -> R.string.preset_smb_hint
        DatasetPreset.NFS -> R.string.preset_nfs_hint
        DatasetPreset.MULTIPROTOCOL -> R.string.preset_multi_hint
        DatasetPreset.APPS -> R.string.preset_apps_hint
    },
)

/**
 * Asks for the passphrase in a window that can't be screenshotted or recorded. The passphrase is
 * only held in memory while the dialog is open and is cleared once sent; it's never saved.
 */
@Composable
private fun UnlockDialog(d: DatasetInfo, onDismiss: () -> Unit, onUnlock: (CharArray) -> Unit) {
    SecureWindow()
    // Not rememberSaveable: a passphrase must never go into saved instance state.
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    CtrlDialog(
        onDismissRequest = {
            pass = ""
            onDismiss()
        },
        // The dialog is its own window: keep it out of screenshots and recordings too.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.dataset_unlock_title, d.id)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dataset_unlock_body))
                CtrlTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.dataset_passphrase)) },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(
                                if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                stringResource(if (show) R.string.hide else R.string.show),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pass.isNotEmpty(),
                onClick = {
                    val chars = pass.toCharArray()
                    pass = ""
                    onUnlock(chars)
                },
            ) { Text(stringResource(R.string.dataset_unlock)) }
        },
        dismissButton = {
            TextButton(onClick = {
                pass = ""
                onDismiss()
            }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
