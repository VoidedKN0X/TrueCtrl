package com.vknxworks.truectrl.ui.system

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.LocalBottomClearance
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.TabTitle
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.components.formatUptime
import com.vknxworks.truectrl.ui.nav.AlertsRoute
import com.vknxworks.truectrl.ui.nav.AppsRoute
import com.vknxworks.truectrl.ui.nav.JobsRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.NetworkRoute
import com.vknxworks.truectrl.ui.nav.ShellRoute
import com.vknxworks.truectrl.ui.nav.UpdatesRoute
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

private const val POLL_MS = 30_000L

/** Opens a Proxmox guest in ProxCtrl, the Ctrl series' Proxmox app (proxctrl://guest/<vmid>). */
fun proxCtrlIntent(vmId: Int): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("proxctrl://guest/$vmId"))

/** The server at a glance, and the way in to apps, alerts, jobs, updates, network, shell and power. */
@Composable
fun SystemScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val overview by c.overview.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.server
    val caps = access.capabilities
    val snap = overview.snapshot
    val info = snap.info
    var refreshing by remember { mutableStateOf(false) }
    var power by remember { mutableStateOf<Boolean?>(null) } // true = shut down, false = reboot
    var editVm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    PollWhileVisible(listOf(server?.id, access.generation), POLL_MS) {
        c.overview.refresh()
        c.overview.state.value.error == null
    }

    val vmId = server?.proxmoxVmId
    val proxCtrl = vmId?.let { id -> proxCtrlIntent(id).takeIf { context.packageManager.resolveActivity(it, 0) != null } }
    val isAdmin = caps?.isFullAdmin == true
    val canReboot = caps?.has("system.reboot") == true && c.sessions.canWrite(Capabilities.FULL_ADMIN)
    val canShutdown = caps?.has("system.shutdown") == true && c.sessions.canWrite(Capabilities.FULL_ADMIN)
    val hostname = info?.hostname?.ifBlank { null } ?: server?.host.orEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_system))
            if (!access.connected && access.error != null) ConnectionBanner()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        if (!access.connected) c.sessions.reconnect()
                        c.overview.refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomClearance.current + 16.dp)) {
                    Group(stringResource(R.string.system_this_server)) {
                        GroupRow(hostname, listOfNotNull(info?.version?.ifBlank { null }, server?.host).distinct().joinToString("  ·  "), Icons.Outlined.Dns)
                        if (info != null) {
                            val uptime = info.uptimeSeconds + if (info.readAtMs > 0) (System.currentTimeMillis() - info.readAtMs) / 1000 else 0
                            GroupRow(
                                stringResource(R.string.system_uptime, formatUptime(uptime)),
                                listOfNotNull(
                                    info.model.ifBlank { null },
                                    if (info.cores > 0) pluralStringResource(R.plurals.cpu_cores, info.cores, info.cores) else null,
                                    if (info.physmem > 0) stringResource(R.string.system_memory, formatBytes(info.physmem)) else null,
                                ).joinToString("  ·  "),
                            )
                        }
                    }
                    Group {
                        if (caps?.has("app.query") == true) {
                            val running = snap.apps.count { it.state == "RUNNING" }
                            val upgrades = snap.apps.count { it.upgradeAvailable }
                            val crashed = snap.apps.count { it.state == "CRASHED" }
                            GroupRow(
                                stringResource(R.string.system_apps),
                                listOfNotNull(
                                    stringResource(R.string.system_apps_sub, running, snap.apps.size),
                                    if (crashed > 0) pluralStringResource(R.plurals.apps_crashed, crashed, crashed) else null,
                                    if (upgrades > 0) pluralStringResource(R.plurals.apps_upgrades, upgrades, upgrades) else null,
                                ).joinToString("  ·  "),
                                Icons.Outlined.Apps,
                                iconTint = if (crashed > 0) Health.critical else MaterialTheme.colorScheme.primary,
                                showChevron = true,
                                onClick = { nav.navigate(AppsRoute) },
                            )
                        }
                        val activeAlerts = snap.alerts.count { !it.dismissed }
                        GroupRow(
                            stringResource(R.string.alerts_title),
                            if (activeAlerts == 0) stringResource(R.string.alerts_none) else pluralStringResource(R.plurals.alerts_active, activeAlerts, activeAlerts),
                            Icons.Outlined.NotificationsActive,
                            showChevron = true,
                            onClick = { nav.navigate(AlertsRoute) },
                        )
                        GroupRow(stringResource(R.string.system_jobs), stringResource(R.string.system_jobs_sub), Icons.Outlined.WorkHistory, showChevron = true, onClick = { nav.navigate(JobsRoute) })
                        val updateApi = caps?.updateApi
                        if (updateApi != null && updateApi != com.vknxworks.truectrl.data.api.UpdateApi.NONE) {
                            val u = snap.update
                            GroupRow(
                                stringResource(R.string.system_updates),
                                when {
                                    u?.applied == true || snap.rebootReasons.isNotEmpty() -> stringResource(R.string.att_reboot)
                                    u?.available == true -> u?.version?.let { stringResource(R.string.att_update_version, it) } ?: stringResource(R.string.att_update)
                                    else -> stringResource(R.string.system_up_to_date)
                                },
                                Icons.Outlined.SystemUpdate,
                                iconTint = if (u?.available == true) Health.warn else MaterialTheme.colorScheme.primary,
                                showChevron = true,
                                onClick = { nav.navigate(UpdatesRoute) },
                            )
                        }
                        if (caps?.has("interface.query") == true) {
                            GroupRow(stringResource(R.string.system_network), stringResource(R.string.system_network_sub), Icons.Outlined.Lan, showChevron = true, onClick = { nav.navigate(NetworkRoute) })
                        }
                        if (caps?.webShell == true && server?.readOnly == false) {
                            GroupRow(stringResource(R.string.shell_title), stringResource(R.string.shell_sub), Icons.Outlined.Terminal, showChevron = true, onClick = { nav.navigate(ShellRoute) })
                        }
                    }
                    Group(
                        stringResource(R.string.system_power),
                        footer = when {
                            server?.readOnly == true -> stringResource(R.string.read_only_hint)
                            !isAdmin -> stringResource(R.string.power_admin_only)
                            else -> null
                        },
                    ) {
                        if (canReboot) {
                            GroupRow(stringResource(R.string.power_reboot), stringResource(R.string.power_reboot_sub), Icons.Outlined.RestartAlt, onClick = { power = false })
                        }
                        if (canShutdown) {
                            GroupRow(
                                stringResource(R.string.power_shutdown),
                                stringResource(R.string.power_shutdown_sub),
                                Icons.Outlined.PowerSettingsNew,
                                iconTint = MaterialTheme.colorScheme.error,
                                onClick = { power = true },
                            )
                        }
                        GroupRow(
                            stringResource(R.string.proxmox_vm),
                            vmId?.let { stringResource(R.string.proxmox_vm_set, it) } ?: stringResource(R.string.proxmox_vm_none),
                            Icons.Outlined.Tune,
                            onClick = { editVm = true },
                        )
                        if (proxCtrl != null) {
                            GroupRow(
                                stringResource(R.string.open_in_proxctrl),
                                stringResource(R.string.open_in_proxctrl_sub, vmId ?: 0),
                                Icons.Outlined.OpenInNew,
                                onClick = { runCatching { context.startActivity(proxCtrl) } },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current))
    }

    power?.let { shutdown ->
        val body = buildString {
            append(stringResource(if (shutdown) R.string.power_shutdown_body else R.string.power_reboot_body, hostname))
            if (shutdown) {
                append("\n\n")
                append(
                    if (vmId != null) stringResource(R.string.power_shutdown_proxmox, vmId)
                    else stringResource(R.string.power_shutdown_physical),
                )
            }
        }
        ConfirmDialog(
            title = stringResource(if (shutdown) R.string.power_shutdown_title else R.string.power_reboot_title, hostname),
            body = body,
            confirmLabel = stringResource(if (shutdown) R.string.power_shutdown else R.string.power_reboot),
            destructive = true,
            typeToConfirm = hostname,
            onConfirm = {
                power = null
                scope.launch {
                    try {
                        c.system.power(shutdown)
                        snackbar.showSnackbar(context.getString(if (shutdown) R.string.power_shutdown_done else R.string.power_reboot_done))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                    }
                }
            },
            onDismiss = { power = null },
        )
    }

    if (editVm && server != null) {
        var text by remember { mutableStateOf(vmId?.toString().orEmpty()) }
        val parsed = text.trim().toIntOrNull()
        val ok = text.isBlank() || (parsed != null && parsed in 100..999_999_999)
        AlertDialog(
            onDismissRequest = { editVm = false },
            title = { Text(stringResource(R.string.proxmox_vm)) },
            text = {
                Column {
                    Text(stringResource(R.string.proxmox_vm_body))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(9) },
                        label = { Text(stringResource(R.string.proxmox_vm_id)) },
                        singleLine = true,
                        isError = !ok,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    c.serverStore.update(server.id) { it.copy(proxmoxVmId = parsed) }
                    editVm = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editVm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A plain list screen body with the navigation bar inset. */
@Composable
internal fun ScreenColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) { content() }
}
