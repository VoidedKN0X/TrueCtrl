package com.vknxworks.truectrl.ui.sharing

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.ServiceInfo
import com.vknxworks.truectrl.data.sharing.NfsForm
import com.vknxworks.truectrl.data.sharing.ShareKind
import com.vknxworks.truectrl.data.sharing.SmbForm
import com.vknxworks.truectrl.data.storage.DatasetInfo
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.CtrlSheet
import com.vknxworks.truectrl.ui.components.CtrlTextField
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupChoice
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.PillButton
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.overview.serviceLabel
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

/** A dataset's path for a share: its mountpoint, or /mnt/<id> when TrueNAS didn't report one. */
internal fun DatasetInfo.sharePath(): String = mountpoint?.takeIf { it.startsWith("/mnt/") } ?: "/mnt/$id"

/** Datasets a share can point at: unlocked filesystems, without TrueNAS's own hidden ones (.system, ix-apps). */
internal fun shareableDatasets(all: List<DatasetInfo>): List<DatasetInfo> = all
    .filter { !it.isZvol && !it.locked }
    .filter { d -> d.id.split('/').drop(1).none { it.startsWith(".") || it == "ix-apps" || it == "ix-applications" } }
    .sortedBy { it.id }

/**
 * Creates or edits an SMB or NFS share ([id] null: a new one). Shows whether the service behind
 * the share is running and offers to start it, and deletes an existing share after asking.
 */
@Composable
fun ShareEditScreen(kind: ShareKind, id: Int?) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val sharing by c.sharing.state.collectAsStateWithLifecycle()
    val datasets by c.datasets.state.collectAsStateWithLifecycle()
    val server = active?.server
    val caps = access.capabilities
    val snackbar = remember { SnackbarHostState() }

    var smb by remember { mutableStateOf(SmbForm()) }
    var nfs by remember { mutableStateOf(NfsForm()) }
    var loaded by remember { mutableStateOf(id == null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var purposes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var nameTouched by remember { mutableStateOf(id != null) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var offerStart by remember { mutableStateOf<ServiceInfo?>(null) }

    val ns = if (kind == ShareKind.SMB) "sharing.smb" else "sharing.nfs"
    val role = if (kind == ShareKind.SMB) "SHARING_SMB_WRITE" else "SHARING_NFS_WRITE"
    val canSave = caps?.has(if (id == null) "$ns.create" else "$ns.update") == true && c.sessions.canWrite(role)
    val canDelete = id != null && caps?.has("$ns.delete") == true && c.sessions.canWrite(role)
    val canStart = (caps?.has("service.control") == true || caps?.has("service.start") == true) &&
        caps?.has("service.update") == true && c.sessions.canWrite("SERVICE_WRITE")
    val newApi = c.sharing.smbNewApi()
    val service = sharing.snapshot.services.firstOrNull { it.service == if (kind == ShareKind.SMB) "cifs" else "nfs" }
    val label = if (kind == ShareKind.SMB) "SMB" else "NFS"

    LaunchedEffect(server?.id, access.generation) {
        if (!access.connected) return@LaunchedEffect
        c.sharing.refresh()
        if (datasets.datasets.isEmpty()) c.datasets.refresh()
        if (kind == ShareKind.SMB) purposes = c.sharing.smbPurposes()
        if (id != null && !loaded) {
            try {
                val raw = c.sharing.shareRaw(kind, id)
                if (raw == null) {
                    loadError = com.vknxworks.truectrl.data.api.TnError.Call("ENOENT", null)
                } else {
                    if (kind == ShareKind.SMB) smb = SmbForm.from(raw) else nfs = NfsForm.from(raw)
                    loaded = true
                }
            } catch (e: Exception) {
                loadError = e
            }
        }
    }

    fun finish(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        nav.back()
    }

    val path = if (kind == ShareKind.SMB) smb.path else nfs.path
    val pathOk = path.trim().startsWith("/mnt/") && path.trim().length > 5
    val nameOk = kind == ShareKind.NFS || SmbForm.nameValid(smb.name)
    val nfsOk = kind == ShareKind.SMB || (!nfs.mappingConflict && nfs.networkList.size <= NfsForm.MAX_ENTRIES && nfs.hostList.size <= NfsForm.MAX_ENTRIES)
    val valid = pathOk && nameOk && nfsOk
    val title = when {
        id == null && kind == ShareKind.SMB -> stringResource(R.string.share_new_smb)
        id == null -> stringResource(R.string.share_new_nfs)
        kind == ShareKind.SMB -> smb.name.ifBlank { stringResource(R.string.share_edit) }
        else -> nfs.path.substringAfterLast('/').ifBlank { stringResource(R.string.share_edit) }
    }

    fun save() {
        busy = true
        scope.launch {
            try {
                if (kind == ShareKind.SMB) c.sharing.saveSmb(id, smb) else c.sharing.saveNfs(id, nfs)
                val shareName = if (kind == ShareKind.SMB) smb.name.trim() else nfs.path.trim()
                val svc = c.sharing.serviceFor(kind)
                val enabled = if (kind == ShareKind.SMB) smb.enabled else nfs.enabled
                if (enabled && canStart && svc != null && (!svc.running || !svc.enable)) {
                    offerStart = svc
                    Toast.makeText(context, context.getString(if (id == null) R.string.share_created else R.string.share_saved, shareName), Toast.LENGTH_SHORT).show()
                } else {
                    finish(context.getString(if (id == null) R.string.share_created else R.string.share_saved, shareName))
                }
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title)
        if (!access.connected && access.error != null) ConnectionBanner()
        when {
            loadError != null -> EmptyState(
                Icons.Outlined.FolderShared,
                stringResource(R.string.share_load_failed),
                errorMessage(context, loadError!!, server?.host.orEmpty()),
                Modifier.weight(1f),
            )
            !loaded -> Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(Icons.Outlined.FolderShared, stringResource(R.string.overview_loading), "")
            }
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                ServiceGroup(label, service, canStart, busy) {
                    busy = true
                    scope.launch {
                        try {
                            c.sharing.startAndEnable(it)
                            snackbar.showSnackbar(context.getString(R.string.service_started, serviceLabel(it.service)))
                        } catch (e: Exception) {
                            snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                        } finally {
                            busy = false
                        }
                    }
                }

                Group(stringResource(R.string.share_group_path), footer = stringResource(R.string.share_path_hint)) {
                    FieldBox {
                        CtrlTextField(
                            value = path,
                            onValueChange = { v ->
                                if (kind == ShareKind.SMB) {
                                    smb = smb.copy(path = v, name = if (nameTouched) smb.name else v.trimEnd('/').substringAfterLast('/'))
                                } else {
                                    nfs = nfs.copy(path = v)
                                }
                            },
                            label = { Text(stringResource(R.string.share_path)) },
                            singleLine = true,
                            enabled = canSave,
                            isError = path.isNotEmpty() && !pathOk,
                            supportingText = if (path.isNotEmpty() && !pathOk) ({ Text(stringResource(R.string.share_path_invalid)) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                            trailingIcon = if (canSave) ({
                                IconButton(onClick = { picker = true }) { Icon(Icons.Outlined.Folder, stringResource(R.string.share_pick_dataset)) }
                            }) else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (canSave) GroupRow(stringResource(R.string.share_pick_dataset), null, Icons.Outlined.Folder, showChevron = true, onClick = { picker = true })
                }

                if (kind == ShareKind.SMB) {
                    SmbFields(smb, newApi, canSave, purposes, onName = { nameTouched = true }) { smb = it }
                } else {
                    NfsFields(nfs, canSave) { nfs = it }
                }

                if (canSave) {
                    PillButton(
                        text = stringResource(if (id == null) R.string.share_create else R.string.save),
                        onClick = ::save,
                        enabled = valid && !busy && access.connected,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                    )
                } else {
                    Text(
                        stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.share_role_hint, role),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp),
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.padding(horizontal = Gutter).size(24.dp), strokeWidth = 2.dp)

                if (canDelete) {
                    Group {
                        GroupRow(
                            stringResource(R.string.share_delete),
                            stringResource(R.string.share_delete_sub),
                            Icons.Outlined.Delete,
                            iconTint = Health.critical,
                            titleColor = Health.critical,
                            onClick = { confirmDelete = true },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    if (picker) {
        DatasetPickerSheet(shareableDatasets(datasets.datasets), onDismiss = { picker = false }) { d ->
            picker = false
            val p = d.sharePath()
            if (kind == ShareKind.SMB) {
                smb = smb.copy(path = p, name = if (nameTouched) smb.name else d.name)
            } else {
                nfs = nfs.copy(path = p)
            }
        }
    }

    if (confirmDelete && id != null) {
        val name = if (kind == ShareKind.SMB) smb.name else nfs.path
        ConfirmDialog(
            title = stringResource(R.string.share_delete_title, name),
            body = stringResource(if (kind == ShareKind.SMB) R.string.share_delete_body_smb else R.string.share_delete_body_nfs),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                busy = true
                scope.launch {
                    try {
                        c.sharing.deleteShare(kind, id)
                        finish(context.getString(R.string.share_deleted, name))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    offerStart?.let { svc ->
        ConfirmDialog(
            title = stringResource(R.string.share_start_service_title, label),
            body = stringResource(
                when {
                    !svc.running && !svc.enable -> R.string.share_start_service_body_both
                    !svc.running -> R.string.share_start_service_body_stopped
                    else -> R.string.share_start_service_body_boot
                },
                label,
            ),
            confirmLabel = stringResource(if (svc.running) R.string.share_start_service_boot else R.string.share_start_service),
            destructive = false,
            onConfirm = {
                offerStart = null
                scope.launch {
                    try {
                        c.sharing.startAndEnable(svc)
                        finish(context.getString(R.string.service_started, label))
                    } catch (e: Exception) {
                        finish(errorMessage(context, e, server?.host.orEmpty()))
                    }
                }
            },
            onDismiss = {
                offerStart = null
                nav.back()
            },
        )
    }
}

@Composable
private fun FieldBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
}

/** Whether the SMB or NFS service runs and starts on boot, with a button to fix it. */
@Composable
private fun ServiceGroup(label: String, service: ServiceInfo?, canStart: Boolean, busy: Boolean, onStart: (ServiceInfo) -> Unit) {
    if (service == null) return
    val ok = service.running && service.enable
    Group(
        stringResource(R.string.share_group_service),
        footer = if (ok) null else stringResource(R.string.share_service_needed, label),
    ) {
        GroupRow(
            title = stringResource(R.string.share_service_title, label),
            subtitle = listOf(
                stringResource(if (service.running) R.string.service_running else R.string.service_stopped),
                stringResource(if (service.enable) R.string.service_on_boot else R.string.service_not_on_boot),
            ).joinToString("  ·  "),
            titleColor = if (ok) CtrlColors.Text else Health.warn,
        )
        if (!ok && canStart && !busy) {
            GroupRow(
                stringResource(if (service.running) R.string.share_start_service_boot else R.string.share_start_service),
                null,
                Icons.Outlined.PlayArrow,
                onClick = { onStart(service) },
            )
        }
    }
}

@Composable
private fun SmbFields(
    f: SmbForm,
    newApi: Boolean,
    enabled: Boolean,
    purposes: List<Pair<String, String>>,
    onName: () -> Unit,
    onChange: (SmbForm) -> Unit,
) {
    val nameOk = f.name.isEmpty() || SmbForm.nameValid(f.name)
    Group(stringResource(R.string.share_group_share)) {
        FieldBox {
            CtrlTextField(
                value = f.name,
                onValueChange = {
                    onName()
                    onChange(f.copy(name = it))
                },
                label = { Text(stringResource(R.string.share_name)) },
                singleLine = true,
                enabled = enabled,
                isError = !nameOk,
                supportingText = if (!nameOk) ({ Text(stringResource(R.string.share_name_invalid)) }) else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FieldBox {
            CtrlTextField(
                value = f.comment,
                onValueChange = { onChange(f.copy(comment = it)) },
                label = { Text(stringResource(R.string.share_comment)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val options = purposes
            .filter { (k, _) -> k !in SmbForm.HIDDEN_PURPOSES || k == f.purpose }
            .map { (k, v) -> k to purposeName(k, v) }
            .let { list -> if (list.none { it.first == f.purpose }) list + (f.purpose to purposeName(f.purpose, f.purpose)) else list }
            .distinctBy { it.second }
        if (enabled && options.size > 1) {
            GroupChoice(stringResource(R.string.share_purpose), options, f.purpose) { onChange(f.copy(purpose = it)) }
        } else {
            GroupRow(stringResource(R.string.share_purpose), options.firstOrNull { it.first == f.purpose }?.second ?: f.purpose)
        }
    }
    val footer = if (!newApi && f.purpose != "NO_PRESET") {
        stringResource(R.string.share_preset_overrides) + "\n\n" + stringResource(R.string.share_acl_hint)
    } else {
        stringResource(R.string.share_acl_hint)
    }
    Group(stringResource(R.string.share_group_access), footer = footer) {
        SwitchRow(stringResource(R.string.share_enabled), stringResource(R.string.share_enabled_sub), f.enabled, enabled) { onChange(f.copy(enabled = it)) }
        SwitchRow(stringResource(R.string.share_readonly), stringResource(R.string.share_readonly_sub_smb), f.readonly, enabled) { onChange(f.copy(readonly = it)) }
        SwitchRow(stringResource(R.string.share_browsable), stringResource(R.string.share_browsable_sub), f.browsable, enabled) { onChange(f.copy(browsable = it)) }
        SwitchRow(stringResource(R.string.share_abe), stringResource(R.string.share_abe_sub), f.abe, enabled) { onChange(f.copy(abe = it)) }
        if (f.guestAvailable(newApi)) {
            SwitchRow(stringResource(R.string.share_guest), stringResource(R.string.share_guest_sub), f.guestOk, enabled) { onChange(f.copy(guestOk = it)) }
        } else {
            GroupRow(stringResource(R.string.share_guest), stringResource(R.string.share_guest_unavailable))
        }
    }
}

@Composable
private fun NfsFields(f: NfsForm, enabled: Boolean, onChange: (NfsForm) -> Unit) {
    Group(stringResource(R.string.share_group_share)) {
        FieldBox {
            CtrlTextField(
                value = f.comment,
                onValueChange = { onChange(f.copy(comment = it)) },
                label = { Text(stringResource(R.string.share_comment)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow(stringResource(R.string.share_enabled), stringResource(R.string.share_enabled_sub), f.enabled, enabled) { onChange(f.copy(enabled = it)) }
        SwitchRow(stringResource(R.string.share_readonly), stringResource(R.string.share_readonly_sub_nfs), f.readonly, enabled) { onChange(f.copy(readonly = it)) }
    }
    Group(stringResource(R.string.share_group_clients), footer = stringResource(R.string.share_clients_hint, NfsForm.MAX_ENTRIES)) {
        FieldBox {
            CtrlTextField(
                value = f.networks,
                onValueChange = { onChange(f.copy(networks = it)) },
                label = { Text(stringResource(R.string.share_networks)) },
                placeholder = { Text("192.168.1.0/24") },
                enabled = enabled,
                minLines = 2,
                isError = f.networkList.size > NfsForm.MAX_ENTRIES,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FieldBox {
            CtrlTextField(
                value = f.hosts,
                onValueChange = { onChange(f.copy(hosts = it)) },
                label = { Text(stringResource(R.string.share_hosts)) },
                placeholder = { Text("192.168.1.20") },
                enabled = enabled,
                minLines = 2,
                isError = f.hostList.size > NfsForm.MAX_ENTRIES,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Group(
        stringResource(R.string.share_group_mapping),
        footer = stringResource(if (f.mappingConflict) R.string.share_mapping_conflict else R.string.share_mapping_hint),
    ) {
        listOf(
            Triple(R.string.share_maproot_user, f.maprootUser, { v: String -> f.copy(maprootUser = v) }),
            Triple(R.string.share_maproot_group, f.maprootGroup, { v: String -> f.copy(maprootGroup = v) }),
            Triple(R.string.share_mapall_user, f.mapallUser, { v: String -> f.copy(mapallUser = v) }),
            Triple(R.string.share_mapall_group, f.mapallGroup, { v: String -> f.copy(mapallGroup = v) }),
        ).forEach { (labelRes, value, update) ->
            FieldBox {
                CtrlTextField(
                    value = value,
                    onValueChange = { onChange(update(it.trim())) },
                    label = { Text(stringResource(labelRes)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = f.mappingConflict && value.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Group(stringResource(R.string.share_security), footer = stringResource(R.string.share_security_hint)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NfsForm.SECURITY.forEach { s ->
                FilterChip(
                    selected = s in f.security,
                    enabled = enabled,
                    onClick = { onChange(f.copy(security = if (s in f.security) f.security - s else f.security + s)) },
                    label = { Text(s) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    if (enabled) {
        GroupSwitch(title, subtitle, checked = checked, onChange = onChange)
    } else {
        GroupRow(title, listOf(subtitle, stringResource(if (checked) R.string.value_on else R.string.value_off)).joinToString("\n"))
    }
}

/** TrueNAS's purpose names in words, falling back to its own description. */
@Composable
private fun purposeName(key: String, verbose: String): String = when (key) {
    "DEFAULT_SHARE" -> stringResource(R.string.purpose_default)
    "LEGACY_SHARE" -> stringResource(R.string.purpose_legacy)
    "NO_PRESET" -> stringResource(R.string.purpose_no_preset)
    "TIMEMACHINE_SHARE", "TIMEMACHINE" -> stringResource(R.string.purpose_timemachine)
    "ENHANCED_TIMEMACHINE" -> stringResource(R.string.purpose_timemachine_multi)
    "MULTIPROTOCOL_SHARE", "MULTI_PROTOCOL_NFS" -> stringResource(R.string.purpose_multiprotocol)
    "TIME_LOCKED_SHARE", "WORM_DROPBOX" -> stringResource(R.string.purpose_time_locked)
    "PRIVATE_DATASETS_SHARE", "PRIVATE_DATASETS" -> stringResource(R.string.purpose_private)
    "EXTERNAL_SHARE" -> stringResource(R.string.purpose_external)
    "VEEAM_REPOSITORY_SHARE" -> stringResource(R.string.purpose_veeam)
    "FCP_SHARE" -> stringResource(R.string.purpose_fcp)
    else -> verbose.ifBlank { key }
}

/** Picks a dataset; its mountpoint becomes the path. */
@Composable
internal fun DatasetPickerSheet(datasets: List<DatasetInfo>, onDismiss: () -> Unit, onPick: (DatasetInfo) -> Unit) {
    CtrlSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.share_pick_dataset), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (datasets.isEmpty()) {
            Text(
                stringResource(R.string.share_no_datasets),
                style = MaterialTheme.typography.bodyMedium,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
            )
        }
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(datasets, key = { it.id }) { d ->
                Column(
                    Modifier.fillMaxWidth().clickable { onPick(d) }.heightIn(min = 52.dp).padding(start = (24 + 12 * d.depth.coerceAtMost(6)).dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Text(d.name, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
                    Text(d.sharePath(), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                }
            }
        }
    }
}
