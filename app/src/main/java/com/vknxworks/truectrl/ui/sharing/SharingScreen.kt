package com.vknxworks.truectrl.ui.sharing

import com.vknxworks.truectrl.ui.components.ctrlSegmentColors
import com.vknxworks.truectrl.ui.components.ctrlSegmentShape
import com.vknxworks.truectrl.ui.components.CtrlSwitch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.ServiceInfo
import com.vknxworks.truectrl.data.sharing.ServiceVerb
import com.vknxworks.truectrl.data.sharing.ShareInfo
import com.vknxworks.truectrl.data.sharing.ShareKind
import com.vknxworks.truectrl.ui.components.ChoiceSheet
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.LocalBottomClearance
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.StaleBanner
import com.vknxworks.truectrl.ui.components.TabTitle
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.ShareEditRoute
import com.vknxworks.truectrl.ui.overview.serviceLabel
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

private const val POLL_MS = 20_000L

private sealed interface SharePending {
    data class Toggle(val share: ShareInfo, val on: Boolean) : SharePending
    data class Control(val service: ServiceInfo, val verb: ServiceVerb) : SharePending
}

/** SMB and NFS shares, who's connected, and the services behind them. */
@Composable
fun SharingScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.sharing.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.server
    val caps = access.capabilities
    var section by rememberSaveable { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SharePending?>(null) }
    var menuFor by remember { mutableStateOf<ServiceInfo?>(null) }
    val snackbar = remember { SnackbarHostState() }

    PollWhileVisible(listOf(server?.id, access.generation), POLL_MS) {
        c.sharing.refresh()
        c.sharing.state.value.error == null
    }

    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                snackbar.showSnackbar(done)
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            }
        }
    }

    fun toggle(share: ShareInfo, on: Boolean) {
        if (on) {
            act(context.getString(R.string.share_enabled_done, share.name.ifBlank { share.path })) { c.sharing.setShareEnabled(share, true) }
        } else {
            pending = SharePending.Toggle(share, false)
        }
    }

    val canSmb = caps?.has("sharing.smb.update") == true && c.sessions.canWrite("SHARING_SMB_WRITE")
    val canNfs = caps?.has("sharing.nfs.update") == true && c.sessions.canWrite("SHARING_NFS_WRITE")
    val canSmbCreate = caps?.has("sharing.smb.create") == true && c.sessions.canWrite("SHARING_SMB_WRITE")
    val canNfsCreate = caps?.has("sharing.nfs.create") == true && c.sessions.canWrite("SHARING_NFS_WRITE")
    val canServiceUpdate = caps?.has("service.update") == true && c.sessions.canWrite("SERVICE_WRITE")
    val canControl = (caps?.has("service.control") == true || caps?.has("service.start") == true) && c.sessions.canWrite("SERVICE_WRITE")
    val snap = state.snapshot

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_sharing))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                listOf(R.string.sharing_shares, R.string.sharing_services).forEachIndexed { i, label ->
                    SegmentedButton(selected = section == i, onClick = { section = i }, shape = ctrlSegmentShape(i, 2), colors = ctrlSegmentColors()) {
                        Text(stringResource(label))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val err = state.error
            if (!access.connected && access.error != null) {
                ConnectionBanner()
            } else if (err != null && state.updatedAt > 0) {
                StaleBanner(state.updatedAt, err, server?.host.orEmpty()) { scope.launch { c.sharing.refresh() } }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        if (!access.connected) c.sessions.reconnect()
                        c.sharing.refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                if (state.updatedAt == 0L) {
                    EmptyState(
                        Icons.Outlined.FolderShared,
                        stringResource(if (access.loading || state.loading) R.string.overview_loading else R.string.overview_not_loaded),
                        stringResource(R.string.sharing_empty_hint),
                        Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                        if (section == 0) {
                            item(key = "smb") {
                                val smbService = snap.services.firstOrNull { it.service == "cifs" }
                                Group(
                                    stringResource(R.string.group_smb),
                                    footer = when {
                                        snap.smb.isEmpty() && !canSmbCreate -> stringResource(R.string.none_set_up)
                                        smbService != null && !smbService.running -> stringResource(R.string.service_not_running_footer, "SMB")
                                        else -> null
                                    },
                                ) {
                                    snap.smb.forEach { s ->
                                        ShareRow(s, canSmb, onOpen = { nav.navigate(ShareEditRoute("SMB", s.id)) }) { on -> toggle(s, on) }
                                    }
                                    if (canSmbCreate) {
                                        GroupRow(stringResource(R.string.share_new_smb), null, Icons.Outlined.Add, showChevron = true, onClick = { nav.navigate(ShareEditRoute("SMB")) })
                                    }
                                }
                            }
                            snap.smbSessions?.let { sessions ->
                                item(key = "sessions") {
                                    Group(
                                        pluralStringResource(R.plurals.smb_sessions, sessions.size, sessions.size),
                                        footer = if (sessions.isEmpty()) stringResource(R.string.smb_sessions_none) else null,
                                    ) {
                                        sessions.forEach { s -> GroupRow(s.user, s.machine.takeIf { it.isNotBlank() }) }
                                    }
                                }
                            }
                            item(key = "nfs") {
                                val nfsService = snap.services.firstOrNull { it.service == "nfs" }
                                Group(
                                    stringResource(R.string.group_nfs),
                                    footer = when {
                                        snap.nfs.isEmpty() && !canNfsCreate -> stringResource(R.string.none_set_up)
                                        nfsService != null && !nfsService.running -> stringResource(R.string.service_not_running_footer, "NFS")
                                        else -> null
                                    },
                                ) {
                                    snap.nfs.forEach { s ->
                                        ShareRow(s, canNfs, onOpen = { nav.navigate(ShareEditRoute("NFS", s.id)) }) { on -> toggle(s, on) }
                                    }
                                    if (canNfsCreate) {
                                        GroupRow(stringResource(R.string.share_new_nfs), null, Icons.Outlined.Add, showChevron = true, onClick = { nav.navigate(ShareEditRoute("NFS")) })
                                    }
                                }
                            }
                        } else {
                            item(key = "services") {
                                Group(
                                    footer = stringResource(if (canControl || canServiceUpdate) R.string.services_footer else R.string.services_footer_readonly),
                                ) {
                                    snap.services.forEach { s ->
                                        GroupRow(
                                            title = serviceLabel(s.service),
                                            subtitle = listOf(
                                                stringResource(if (s.running) R.string.service_running else R.string.service_stopped),
                                                stringResource(if (s.enable) R.string.service_on_boot else R.string.service_not_on_boot),
                                            ).joinToString("  ·  "),
                                            titleColor = if (s.enable && !s.running) Health.warn else MaterialTheme.colorScheme.onSurface,
                                            onClick = if (canControl) ({ menuFor = s }) else null,
                                            trailing = if (canServiceUpdate) {
                                                {
                                                    CtrlSwitch(checked = s.enable, onCheckedChange = { on ->
                                                        act(context.getString(if (on) R.string.service_boot_on_done else R.string.service_boot_off_done, serviceLabel(s.service))) {
                                                            c.sharing.setServiceEnabled(s, on)
                                                        }
                                                    })
                                                }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current))
    }

    menuFor?.let { s ->
        val verbs = if (s.running) listOf(ServiceVerb.RESTART, ServiceVerb.STOP) else listOf(ServiceVerb.START)
        ChoiceSheet(
            title = serviceLabel(s.service),
            options = verbs.map { it to stringResource(verbLabel(it)) },
            selected = null,
            onDismiss = { menuFor = null },
        ) { verb ->
            menuFor = null
            if (verb == ServiceVerb.START) {
                act(context.getString(R.string.service_started, serviceLabel(s.service))) { c.sharing.control(s, verb) }
            } else {
                pending = SharePending.Control(s, verb)
            }
        }
    }

    when (val p = pending) {
        null -> Unit
        is SharePending.Toggle -> {
            val name = p.share.name.ifBlank { p.share.path }
            ConfirmDialog(
                title = stringResource(R.string.share_disable_title, name),
                body = stringResource(R.string.share_disable_body),
                confirmLabel = stringResource(R.string.share_disable),
                destructive = true,
                onConfirm = {
                    pending = null
                    act(context.getString(R.string.share_disabled_done, name)) { c.sharing.setShareEnabled(p.share, false) }
                },
                onDismiss = { pending = null },
            )
        }
        is SharePending.Control -> {
            val label = serviceLabel(p.service.service)
            ConfirmDialog(
                title = stringResource(if (p.verb == ServiceVerb.STOP) R.string.service_stop_title else R.string.service_restart_title, label),
                body = stringResource(if (p.verb == ServiceVerb.STOP) R.string.service_stop_body else R.string.service_restart_body),
                confirmLabel = stringResource(verbLabel(p.verb)),
                destructive = p.verb == ServiceVerb.STOP,
                onConfirm = {
                    pending = null
                    act(context.getString(if (p.verb == ServiceVerb.STOP) R.string.service_stopped_done else R.string.service_restarted, label)) {
                        c.sharing.control(p.service, p.verb)
                    }
                },
                onDismiss = { pending = null },
            )
        }
    }
}

private fun verbLabel(v: ServiceVerb): Int = when (v) {
    ServiceVerb.START -> R.string.service_start
    ServiceVerb.STOP -> R.string.service_stop
    ServiceVerb.RESTART -> R.string.service_restart
}

@Composable
private fun ShareRow(s: ShareInfo, canEdit: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val details = listOfNotNull(
        s.path.takeIf { s.kind == ShareKind.SMB && it.isNotBlank() },
        s.comment.takeIf { it.isNotBlank() },
        s.extra.takeIf { it.isNotBlank() && it != "DEFAULT_SHARE" && it != "LEGACY_SHARE" }?.let { purposeLabel(it) },
        if (s.readonly) stringResource(R.string.share_readonly) else null,
        if (s.locked) stringResource(R.string.share_locked) else null,
        if (!s.enabled) stringResource(R.string.share_off) else null,
    )
    GroupRow(
        title = s.name.ifBlank { s.path },
        subtitle = details.joinToString("\n"),
        titleColor = when {
            s.locked -> Health.warn
            !s.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        // Opens the share's settings (read-only without the role; delete is there too).
        showChevron = true,
        onClick = onOpen,
        trailing = if (canEdit) ({ CtrlSwitch(checked = s.enabled, onCheckedChange = onToggle) }) else null,
    )
}

/** TrueNAS share purposes in words; unknown ones are shown as TrueNAS names them. */
@Composable
private fun purposeLabel(p: String): String = when (p) {
    "TIMEMACHINE_SHARE", "ENHANCED_TIMEMACHINE" -> stringResource(R.string.purpose_timemachine)
    "MULTIPROTOCOL_SHARE", "MULTI_PROTOCOL_NFS" -> stringResource(R.string.purpose_multiprotocol)
    "TIME_LOCKED_SHARE", "TIME_LOCKED" -> stringResource(R.string.purpose_time_locked)
    "PRIVATE_DATASETS_SHARE", "PRIVATE_DATASETS" -> stringResource(R.string.purpose_private)
    "EXTERNAL_SHARE" -> stringResource(R.string.purpose_external)
    "VEEAM_REPOSITORY_SHARE" -> stringResource(R.string.purpose_veeam)
    "FCP_SHARE" -> stringResource(R.string.purpose_fcp)
    "READ_ONLY" -> stringResource(R.string.share_readonly)
    else -> p.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
