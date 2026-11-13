package com.vknxworks.truectrl.ui.settings

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.components.rememberNotificationAccess
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupChoice
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.vknxworks.truectrl.data.notify.CheckStore
import com.vknxworks.truectrl.data.notify.NotifyEvent
import com.vknxworks.truectrl.data.notify.NotifySettings
import com.vknxworks.truectrl.data.notify.NotifyWorker
import com.vknxworks.truectrl.data.notify.Push
import com.vknxworks.truectrl.data.notify.PushStatus
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.ui.components.copySensitive
import com.vknxworks.truectrl.ui.components.errorMessage

/** Background checks, push from TrueNAS through ntfy, per-event switches and battery advice. */
@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val lastCheck by CheckStore.lastCheck.collectAsStateWithLifecycle()
    val pushStatus by Push.status.collectAsStateWithLifecycle()
    // Re-checked after the permission dialog and whenever the screen comes back (from Android's settings).
    val notificationAccess = rememberNotificationAccess()
    val canPost = notificationAccess.canPost
    var batteryVersion by remember { mutableIntStateOf(0) }
    val unrestricted = remember(batteryVersion) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    var serverSetUp by remember { mutableStateOf<Boolean?>(null) }
    var serverBusy by remember { mutableStateOf(false) }

    // Re-check permission, battery and push state when coming back from system settings or ntfy.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            batteryVersion++
            servers.forEach { CheckStore.read(context, it.id) }
            Push.load(context, c.notifySettings.current().push)
        }
    }
    val endpoint = (pushStatus as? PushStatus.Registered)?.endpoint
    LaunchedEffect(endpoint, access.generation) {
        serverSetUp = null
        val conn = active?.connection ?: return@LaunchedEffect
        if (endpoint != null && access.connected && access.capabilities?.has("alertservice.query") == true) {
            serverSetUp = Push.isSetUp(conn, endpoint)
        }
    }

    /** Turning a notification feature on asks for the permission (or opens Android's settings) when notifications are blocked. */
    fun ensurePermission() {
        if (!canPost) notificationAccess.fix()
    }
    fun update(transform: (NotifySettings) -> NotifySettings) {
        c.appScope.launch { c.notifySettings.update(transform) }
    }
    fun copy(text: String) {
        // The push address is a secret ntfy topic: keep it out of clipboard previews.
        copySensitive(context, "TrueCtrl", text)
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
    fun startPush() {
        if (Push.ntfyInstalled(context)) Push.register(context) else Push.load(context, true)
    }
    fun installNtfy() {
        runCatching {
            com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Push.NTFY_DOWNLOAD)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun onServer(action: suspend () -> Unit, done: Int) {
        serverBusy = true
        scope.launch {
            try {
                action()
                Toast.makeText(context, context.getString(done), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, errorMessage(context, e, active?.server?.host.orEmpty()), Toast.LENGTH_LONG).show()
            } finally {
                serverBusy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.notifications))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.notif_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )

            if (!canPost && (settings.polling || settings.push)) {
                Surface(
                    shape = CtrlShapes.Card,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, CtrlColors.Error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.notif_permission_off), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { notificationAccess.fix() }, colors = ButtonDefaults.textButtonColors(contentColor = CtrlColors.Error)) { Text(stringResource(R.string.notif_permission_allow)) }
                    }
                }
            }

            // --- Background checks ---
            Group(stringResource(R.string.group_polling), footer = stringResource(R.string.polling_footer)) {
                GroupSwitch(
                    stringResource(R.string.polling_title),
                    stringResource(R.string.polling_subtitle),
                    // Shown off while notifications are blocked: the checks can't tell you anything then.
                    checked = settings.polling && canPost,
                    icon = Icons.Outlined.Schedule,
                ) { on ->
                    if (on) ensurePermission()
                    update { it.copy(polling = on) }
                }
                if (settings.polling && canPost) {
                    GroupChoice(
                        stringResource(R.string.polling_interval),
                        NotifySettings.INTERVALS.map { it to pluralStringResource(R.plurals.polling_interval_value, it, it) },
                        settings.intervalMinutes,
                        icon = Icons.Outlined.Timer,
                    ) { m -> update { it.copy(intervalMinutes = m) } }
                }
                servers.forEach { server ->
                    val st = lastCheck[server.id]
                    val subtitle = when {
                        st == null || st.lastCheckAt == 0L -> stringResource(R.string.last_check_never)
                        st.lastResult == "ok" -> stringResource(R.string.last_check, relativeTime(st.lastCheckAt))
                        else -> stringResource(R.string.last_check_failed, relativeTime(st.lastCheckAt))
                    }
                    GroupRow(server.name, subtitle)
                }
                GroupRow(
                    stringResource(R.string.check_now),
                    icon = Icons.Outlined.Refresh,
                    onClick = {
                        notificationAccess.askIfPossible()
                        NotifyWorker.runNow(context)
                        Toast.makeText(context, context.getString(R.string.check_started), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            // --- Push ---
            Group(stringResource(R.string.group_push), footer = if (settings.push) stringResource(R.string.push_dedupe_note) else null) {
                GroupSwitch(
                    stringResource(R.string.push_title),
                    stringResource(R.string.push_subtitle),
                    checked = settings.push && canPost,
                    icon = Icons.Outlined.Campaign,
                ) { on ->
                    update { it.copy(push = on) }
                    if (on) {
                        ensurePermission()
                        startPush()
                    } else {
                        Push.unregister(context)
                    }
                }
                if (settings.push && canPost) {
                    when (val st = pushStatus) {
                        PushStatus.NtfyMissing -> PushNote(stringResource(R.string.push_no_distributor)) {
                            TextButton(onClick = ::installNtfy) { Text(stringResource(R.string.push_install_ntfy)) }
                        }
                        PushStatus.Off, PushStatus.Registering -> PushNote(stringResource(R.string.push_registering)) {
                            TextButton(onClick = ::startPush) { Text(stringResource(R.string.retry)) }
                        }
                        is PushStatus.Failed -> PushNote(stringResource(R.string.push_failed, st.reason)) {
                            TextButton(onClick = ::startPush) { Text(stringResource(R.string.retry)) }
                        }
                        is PushStatus.Registered -> {
                            if (st.tempUnavailable) {
                                PushNote(stringResource(R.string.push_temp_unavailable)) {}
                            }
                            GroupRow(
                                stringResource(R.string.push_endpoint),
                                st.endpoint,
                                icon = Icons.Outlined.NotificationsActive,
                                trailing = { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) },
                                onClick = { copy(st.endpoint) },
                            )
                            val server = active?.server
                            val conn = active?.connection
                            val canSetUp = server != null && conn != null && access.connected &&
                                access.capabilities?.has("alertservice.create") == true && c.sessions.canWrite("ALERT_WRITE")
                            if (server != null) {
                                Text(
                                    when (serverSetUp) {
                                        true -> stringResource(R.string.push_server_ready, server.name)
                                        false -> stringResource(R.string.push_server_missing, server.name)
                                        null -> stringResource(R.string.push_server_unknown, server.name)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            if (canSetUp && server != null && conn != null) {
                                GroupRow(
                                    stringResource(R.string.push_set_up, server.name),
                                    stringResource(R.string.push_set_up_sub),
                                    icon = Icons.Outlined.CloudUpload,
                                    onClick = {
                                        if (!serverBusy) onServer({
                                            Push.setUpOnServer(conn, st.endpoint, settings.minAlertLevel, access.capabilities?.version)
                                            serverSetUp = true
                                        }, R.string.push_set_up_done)
                                    },
                                )
                                GroupRow(
                                    stringResource(R.string.push_test),
                                    icon = Icons.Outlined.Send,
                                    onClick = {
                                        if (!serverBusy) onServer({
                                            Push.testOnServer(conn, st.endpoint, settings.minAlertLevel, access.capabilities?.version)
                                        }, R.string.push_test_sent)
                                    },
                                )
                            } else {
                                ManualSetup()
                            }
                        }
                    }
                }
            }

            // --- What to notify ---
            Group(stringResource(R.string.group_events)) {
                GroupChoice(
                    stringResource(R.string.min_level),
                    listOf(Severity.CRITICAL, Severity.WARNING, Severity.INFO).map {
                        it to stringResource(
                            when (it) {
                                Severity.CRITICAL -> R.string.min_level_critical
                                Severity.WARNING -> R.string.min_level_warning
                                Severity.INFO -> R.string.min_level_info
                            },
                        )
                    },
                    settings.minAlertLevel,
                    icon = Icons.Outlined.Tune,
                ) { v -> update { it.copy(minAlertLevel = v) } }
                NotifyEvent.entries.forEach { e ->
                    val coveredByPush = settings.push && (e == NotifyEvent.NEW_ALERT || e == NotifyEvent.SMART)
                    GroupSwitch(
                        stringResource(e.label),
                        if (coveredByPush) stringResource(e.summary) + "\n" + stringResource(R.string.ev_sent_by_truenas) else stringResource(e.summary),
                        checked = settings.on(e) && canPost,
                    ) { on ->
                        if (on) ensurePermission()
                        update { s -> s.copy(disabled = if (on) s.disabled - e else s.disabled + e) }
                    }
                }
            }

            // --- Battery ---
            Group(stringResource(R.string.group_battery), footer = stringResource(R.string.battery_footer)) {
                GroupRow(
                    stringResource(if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted),
                    icon = if (unrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                    iconTint = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                GroupRow(
                    stringResource(R.string.battery_open),
                    showChevron = true,
                    onClick = {
                        // App info is where ColorOS keeps "Allow background activity"; fall back to the battery list.
                        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(appInfo) }.onFailure {
                            runCatching {
                                com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PushNote(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

/** The steps to add the alert service by hand, when TrueCtrl can't do it (no Alert Write role, or offline). */
@Composable
private fun ManualSetup() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.push_manual_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.push_manual_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CtrlColors.Raised)
                .padding(10.dp),
        )
    }
}
