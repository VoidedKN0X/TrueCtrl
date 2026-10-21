package com.vknxworks.truectrl.ui.system

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.system.UpdateStatus
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.PillButton
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.JobsRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

/** The TrueNAS version, whether a newer one is out, and installing it. */
@Composable
fun UpdatesScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val overview by c.overview.state.collectAsStateWithLifecycle()
    val server = active?.server
    var status by remember { mutableStateOf<UpdateStatus?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val canUpdate = c.system.updateMethodAvailable() && c.sessions.canWrite("SYSTEM_UPDATE_WRITE")
    val hostname = overview.snapshot.info?.hostname?.ifBlank { null } ?: server?.host.orEmpty()
    val rebootPending = overview.snapshot.rebootReasons

    suspend fun check() {
        checking = true
        try {
            status = c.system.updateStatus()
            error = null
        } catch (e: TnError) {
            error = e
        } finally {
            checking = false
        }
    }

    LaunchedEffect(server?.id, access.generation) { if (access.connected) check() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.system_updates))
        if (!access.connected && access.error != null) ConnectionBanner()
        PullToRefreshBox(isRefreshing = checking, onRefresh = { scope.launch { check() } }, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                val s = status
                Group {
                    GroupRow(
                        stringResource(R.string.update_current),
                        s?.current ?: overview.snapshot.info?.version ?: "",
                    )
                    when {
                        error != null -> GroupRow(
                            stringResource(R.string.update_check_failed),
                            errorMessage(context, error!!, server?.host.orEmpty()),
                            Icons.Outlined.ErrorOutline,
                            iconTint = Health.critical,
                        )
                        s == null -> GroupRow(stringResource(R.string.update_checking), null, Icons.Outlined.SystemUpdate)
                        s.error != null -> GroupRow(stringResource(R.string.update_check_failed), s.error, Icons.Outlined.ErrorOutline, iconTint = Health.critical)
                        s.rebootRequired || rebootPending.isNotEmpty() -> GroupRow(
                            stringResource(R.string.att_reboot),
                            rebootPending.joinToString("\n").ifBlank { stringResource(R.string.update_reboot_pending) },
                            Icons.Outlined.SystemUpdate,
                            iconTint = Health.warn,
                        )
                        s.newVersion != null -> GroupRow(
                            stringResource(R.string.att_update_version, s.newVersion),
                            stringResource(R.string.update_available_sub),
                            Icons.Outlined.SystemUpdate,
                            iconTint = Health.warn,
                        )
                        else -> GroupRow(stringResource(R.string.system_up_to_date), null, Icons.Outlined.CheckCircle, iconTint = Health.ok)
                    }
                    // Only web addresses: the server decides what this link is.
                    s?.releaseNotesUrl?.let { url -> webUri(url)?.let { url to it } }?.let { (url, uri) ->
                        GroupRow(stringResource(R.string.update_release_notes), url, Icons.Outlined.Description, onClick = {
                            runCatching { com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        })
                    }
                }
                s?.downloadPercent?.let { p ->
                    Group(stringResource(R.string.update_downloading)) {
                        LinearProgressIndicator(
                            progress = { (p / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
                if (s?.newVersion != null) {
                    if (canUpdate) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                            PillButton(stringResource(R.string.update_install), onClick = { confirm = true }, icon = Icons.Outlined.SystemUpdate)
                        }
                    } else {
                        Text(
                            stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.update_role_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    val target = status?.newVersion
    if (confirm && target != null) {
        UpdateDialog(
            version = target,
            hostname = hostname,
            onDismiss = { confirm = false },
            onConfirm = { reboot ->
                confirm = false
                scope.launch {
                    try {
                        c.system.runUpdate(reboot)
                        val result = snackbar.showSnackbar(
                            context.getString(if (reboot) R.string.update_started_reboot else R.string.update_started),
                            actionLabel = context.getString(R.string.system_jobs),
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) nav.navigate(JobsRoute)
                    } catch (e: Exception) {
                        snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                    }
                }
            },
        )
    }
}

/** Confirms installing [version]: type the hostname, and choose whether TrueNAS reboots right after. */
@Composable
private fun UpdateDialog(version: String, hostname: String, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var typed by remember { mutableStateOf("") }
    var reboot by remember { mutableStateOf(true) }
    val ok = typed == hostname
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_confirm_title, version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.update_confirm_body))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reboot, onCheckedChange = { reboot = it })
                    Text(stringResource(R.string.update_reboot_after), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.confirm_type, hostname), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reboot) }, enabled = ok) {
                Text(stringResource(R.string.update_install), color = if (ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
