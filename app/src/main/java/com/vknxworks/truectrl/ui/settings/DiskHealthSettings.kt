package com.vknxworks.truectrl.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.notify.DiskHealthStore
import com.vknxworks.truectrl.data.notify.DiskHealthWorker
import com.vknxworks.truectrl.data.notify.Ntfy
import com.vknxworks.truectrl.data.notify.NtfyHttpException
import com.vknxworks.truectrl.data.notify.NtfyProblem
import com.vknxworks.truectrl.data.notify.NtfyTarget
import com.vknxworks.truectrl.data.notify.NotifySettings
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.ui.components.CtrlTextField
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupChoice
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.SecureWindow
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.setup.CertInfo
import com.vknxworks.truectrl.ui.setup.CertTrustDialog
import com.vknxworks.truectrl.ui.setup.fetchCertInfo
import kotlinx.coroutines.launch

/**
 * Settings › Notifications › Disk health: the separate disk-health check (off by default), its interval,
 * and optionally posting new warnings to a topic on the user's own ntfy server.
 */
@Composable
fun DiskHealthSettings(settings: NotifySettings, canPost: Boolean, ensurePermission: () -> Unit, update: ((NotifySettings) -> NotifySettings) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val last by DiskHealthStore.last.collectAsStateWithLifecycle()
    LaunchedEffect(servers) { servers.forEach { DiskHealthStore.read(context, it.id) } }

    var server by remember(settings.ntfyServer) { mutableStateOf(settings.ntfyServer) }
    var topic by remember(settings.ntfyTopic) { mutableStateOf(settings.ntfyTopic) }
    var token by remember { mutableStateOf(c.serverStore.ntfyToken()) }
    var busy by remember { mutableStateOf(false) }
    var cert by remember { mutableStateOf<Pair<CertInfo, NtfyTarget>?>(null) }

    Group(stringResource(R.string.group_disk_health), footer = stringResource(R.string.dh_footer)) {
        GroupSwitch(
            stringResource(R.string.dh_switch),
            stringResource(R.string.dh_switch_sub),
            checked = settings.diskHealth && canPost,
            icon = Icons.Outlined.Storage,
        ) { on ->
            if (on) ensurePermission()
            update { it.copy(diskHealth = on) }
        }
        if (settings.diskHealth && canPost) {
            GroupChoice(
                stringResource(R.string.dh_interval),
                NotifySettings.DISK_INTERVALS.map { m ->
                    m to if (m < 60) pluralStringResource(R.plurals.polling_interval_value, m, m) else pluralStringResource(R.plurals.dh_interval_hours, m / 60, m / 60)
                },
                settings.diskHealthMinutes,
                icon = Icons.Outlined.Timer,
            ) { m -> update { it.copy(diskHealthMinutes = m) } }
            servers.forEach { s ->
                val st = last[s.id]
                GroupRow(
                    s.name,
                    when {
                        st == null || st.lastCheckAt == 0L -> stringResource(R.string.last_check_never)
                        st.lastResult != "ok" -> stringResource(R.string.last_check_failed, relativeTime(st.lastCheckAt))
                        st.active.isEmpty() -> stringResource(R.string.dh_last_ok, relativeTime(st.lastCheckAt))
                        else -> pluralStringResource(R.plurals.dh_last_problems, st.active.size, relativeTime(st.lastCheckAt), st.active.size)
                    },
                )
            }
            GroupRow(stringResource(R.string.dh_check_now), icon = Icons.Outlined.Refresh, onClick = {
                DiskHealthWorker.runNow(context)
                Toast.makeText(context, context.getString(R.string.check_started), Toast.LENGTH_SHORT).show()
            })

            GroupSwitch(
                stringResource(R.string.dh_ntfy_switch),
                stringResource(R.string.dh_ntfy_switch_sub),
                checked = settings.ntfyPost,
                icon = Icons.Outlined.Send,
            ) { on -> update { it.copy(ntfyPost = on) } }
        }
    }

    if (settings.diskHealth && canPost && settings.ntfyPost) {
        // The access token is typed here: keep this screen out of screenshots while it shows.
        SecureWindow()
        val (_, problem) = Ntfy.target(server, topic, token, settings.ntfyCertPin)
        Group(stringResource(R.string.dh_ntfy_group), footer = stringResource(R.string.dh_ntfy_footer)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                CtrlTextField(
                    value = server,
                    onValueChange = { server = it.trim().take(300) },
                    label = { Text(stringResource(R.string.dh_ntfy_server)) },
                    placeholder = { Text("https://ntfy.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                CtrlTextField(
                    value = topic,
                    onValueChange = { topic = it.trim().take(64) },
                    label = { Text(stringResource(R.string.dh_ntfy_topic)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                CtrlTextField(
                    value = token,
                    onValueChange = { token = it.trim().take(200) },
                    label = { Text(stringResource(R.string.dh_ntfy_token)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val message = when (problem) {
                    NtfyProblem.BAD_SERVER -> stringResource(R.string.dh_ntfy_bad_server)
                    NtfyProblem.BAD_TOPIC -> stringResource(R.string.dh_ntfy_bad_topic)
                    NtfyProblem.TOKEN_OVER_HTTP -> stringResource(R.string.dh_ntfy_token_http)
                    else -> if (server.startsWith("http://", ignoreCase = true)) stringResource(R.string.dh_ntfy_http_note) else null
                }
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (settings.ntfyCertPin.isNotBlank()) {
                GroupRow(
                    stringResource(R.string.dh_ntfy_cert_pinned),
                    settings.ntfyCertPin,
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = { update { it.copy(ntfyCertPin = "") } },
                    trailing = { Text(stringResource(R.string.dh_ntfy_cert_forget), color = MaterialTheme.colorScheme.primary) },
                )
            }
            GroupRow(
                stringResource(R.string.dh_ntfy_save_test),
                stringResource(R.string.dh_ntfy_save_test_sub),
                icon = Icons.Outlined.Dns,
                onClick = onClick@{
                    if (busy) return@onClick
                    val (target, p) = Ntfy.target(server, topic, token, settings.ntfyCertPin)
                    if (target == null) {
                        Toast.makeText(context, context.getString(if (p == NtfyProblem.NO_SERVER || p == NtfyProblem.NO_TOPIC) R.string.dh_ntfy_missing else R.string.dh_ntfy_fix), Toast.LENGTH_LONG).show()
                        return@onClick
                    }
                    busy = true
                    scope.launch {
                        try {
                            c.notifySettings.update { it.copy(ntfyServer = target.server.toString().trimEnd('/'), ntfyTopic = target.topic) }
                            c.serverStore.saveNtfyToken(target.token)
                            send(context, c.http, target)
                            Toast.makeText(context, context.getString(R.string.dh_ntfy_sent), Toast.LENGTH_SHORT).show()
                        } catch (e: TnError.UntrustedCertificate) {
                            // A self-signed certificate: show its fingerprint and let the user decide.
                            cert = runCatching { fetchCertInfo(target.server) to target }.getOrNull()
                            if (cert == null) Toast.makeText(context, errorMessage(context, e, target.server.host), Toast.LENGTH_LONG).show()
                        } catch (e: TnError.PinMismatch) {
                            cert = runCatching { fetchCertInfo(target.server) to target }.getOrNull()
                            if (cert == null) Toast.makeText(context, errorMessage(context, e, target.server.host), Toast.LENGTH_LONG).show()
                        } catch (e: NtfyHttpException) {
                            Toast.makeText(context, context.getString(if (e.code == 401 || e.code == 403) R.string.dh_ntfy_denied else R.string.dh_ntfy_http_error, e.code), Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, errorMessage(context, e, target.server.host), Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }

    cert?.let { (info, target) ->
        CertTrustDialog(
            cert = info,
            changed = target.certPin.isNotBlank(),
            onTrust = {
                cert = null
                update { it.copy(ntfyCertPin = info.fingerprint) }
                scope.launch {
                    try {
                        send(context, c.http, target.copy(certPin = info.fingerprint))
                        Toast.makeText(context, context.getString(R.string.dh_ntfy_sent), Toast.LENGTH_SHORT).show()
                    } catch (e: NtfyHttpException) {
                        Toast.makeText(context, context.getString(if (e.code == 401 || e.code == 403) R.string.dh_ntfy_denied else R.string.dh_ntfy_http_error, e.code), Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, errorMessage(context, e, target.server.host), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancel = { cert = null },
        )
    }
}

private suspend fun send(context: android.content.Context, http: okhttp3.OkHttpClient, target: NtfyTarget) {
    Ntfy.publish(http, target, context.getString(R.string.dh_ntfy_test_title), context.getString(R.string.dh_ntfy_test_body), Severity.INFO)
}
