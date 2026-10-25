package com.vknxworks.truectrl.ui.system

import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.components.CtrlSheet
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.system.AppDetail
import com.vknxworks.truectrl.data.system.AppVerb
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.AppLogsRoute
import com.vknxworks.truectrl.ui.nav.CatalogRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

/** Installed apps: state, version, upgrades, web UI links, start/stop/redeploy/upgrade and logs. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var apps by remember { mutableStateOf<List<AppDetail>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<AppDetail, AppVerb>?>(null) }
    var uninstall by remember { mutableStateOf<AppDetail?>(null) }
    val busy = remember { mutableStateListOf<String>() }
    val snackbar = remember { SnackbarHostState() }
    val canWrite = c.sessions.canWrite("APPS_WRITE")
    val caps = access.capabilities
    val canUninstall = canWrite && caps?.has("app.delete") == true
    val canBrowse = caps?.has("app.available") == true

    suspend fun load(): Boolean = try {
        apps = c.system.apps()
        error = null
        true
    } catch (e: TnError) {
        error = e
        false
    }

    PollWhileVisible(listOf(server?.id, access.generation), 10_000L) { if (access.connected) load() else false }

    fun doApp(app: AppDetail, verb: AppVerb) {
        busy += app.name
        scope.launch {
            try {
                c.system.app(app.name, verb)
                load()
                c.overview.refresh()
                snackbar.showSnackbar(context.getString(verbDone(verb), app.name))
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy -= app.name
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.system_apps))
        if (!access.connected && access.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    load()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            val list = apps
            when {
                list == null && error != null -> EmptyState(Icons.Outlined.Apps, stringResource(R.string.apps_failed), errorMessage(context, error!!, server?.host.orEmpty()))
                list == null -> EmptyState(Icons.Outlined.Apps, stringResource(R.string.overview_loading), "")
                list.isEmpty() -> EmptyState(
                    Icons.Outlined.Apps,
                    stringResource(R.string.apps_none),
                    stringResource(R.string.apps_none_body),
                    action = if (canBrowse) ({
                        com.vknxworks.truectrl.ui.components.CtrlOutlinedButton(onClick = { nav.navigate(CatalogRoute) }) { Text(stringResource(R.string.catalog_browse)) }
                    }) else null,
                )
                else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Group(footer = if (!canWrite) stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.apps_role_hint) else null) {
                            list.forEach { app ->
                                GroupRow(
                                    title = app.name,
                                    subtitle = listOfNotNull(
                                        stateLabel(app.state),
                                        app.version.ifBlank { null },
                                        if (app.upgradeAvailable) stringResource(R.string.app_upgrade_to, app.latestVersion ?: "") else null,
                                        if (app.imageUpdates) stringResource(R.string.app_image_updates) else null,
                                    ).joinToString("  ·  "),
                                    titleColor = stateColor(app.state),
                                    showChevron = app.name !in busy,
                                    onClick = { sheetFor = app.name },
                                    trailing = if (app.name in busy) ({ CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) }) else null,
                                )
                            }
                        }
                    }
                    if (canBrowse) {
                        item {
                            Group {
                                GroupRow(stringResource(R.string.catalog_browse), stringResource(R.string.catalog_sub), Icons.Outlined.Storefront, showChevron = true, onClick = { nav.navigate(CatalogRoute) })
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    val app = apps?.firstOrNull { it.name == sheetFor }
    if (app != null) {
        CtrlSheet(onDismissRequest = { sheetFor = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(app.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    listOfNotNull(stateLabel(app.state), app.version.ifBlank { null }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                val idle = app.name !in busy
                app.portals.forEach { (name, url) ->
                    // Only web addresses: the server decides what's in this list.
                    val uri = webUri(url) ?: return@forEach
                    GroupRow(stringResource(R.string.app_open_portal, name), url, Icons.Outlined.Language, onClick = {
                        runCatching { com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    })
                }
                if (canWrite && idle) {
                    when (app.state) {
                        "STOPPED", "CRASHED" -> GroupRow(stringResource(R.string.app_start), null, Icons.Outlined.PlayArrow, onClick = {
                            sheetFor = null
                            doApp(app, AppVerb.START)
                        })
                        "RUNNING", "DEPLOYING" -> GroupRow(stringResource(R.string.app_stop), null, Icons.Outlined.Stop, onClick = {
                            sheetFor = null
                            confirm = app to AppVerb.STOP
                        })
                    }
                    if (app.state != "STOPPING") {
                        GroupRow(stringResource(R.string.app_redeploy), stringResource(R.string.app_redeploy_sub), Icons.Outlined.Refresh, onClick = {
                            sheetFor = null
                            confirm = app to AppVerb.REDEPLOY
                        })
                    }
                    if (app.upgradeAvailable) {
                        GroupRow(stringResource(R.string.app_upgrade), stringResource(R.string.app_upgrade_to, app.latestVersion ?: ""), Icons.Outlined.Upgrade, onClick = {
                            sheetFor = null
                            confirm = app to AppVerb.UPGRADE
                        })
                    }
                }
                if (canUninstall && idle) {
                    GroupRow(
                        stringResource(R.string.app_uninstall),
                        stringResource(R.string.app_uninstall_sub),
                        Icons.Outlined.Delete,
                        iconTint = Health.critical,
                        titleColor = Health.critical,
                        onClick = {
                            sheetFor = null
                            uninstall = app
                        },
                    )
                }
                app.containers.forEach { ct ->
                    GroupRow(
                        stringResource(R.string.app_logs, ct.service.ifBlank { ct.id.take(12) }),
                        listOf(ct.state, ct.image).filter { it.isNotBlank() }.joinToString("  ·  "),
                        Icons.Outlined.Article,
                        showChevron = true,
                        onClick = {
                            sheetFor = null
                            nav.navigate(AppLogsRoute(app.name, ct.id, ct.service))
                        },
                    )
                }
            }
        }
    }

    uninstall?.let { a ->
        UninstallDialog(a, onDismiss = { uninstall = null }) { removeVolumes ->
            uninstall = null
            busy += a.name
            scope.launch {
                try {
                    c.catalog.uninstall(a.name, removeVolumes)
                    c.catalog.forget()
                    load()
                    c.overview.refresh()
                    snackbar.showSnackbar(context.getString(R.string.app_uninstalled, a.name))
                } catch (e: Exception) {
                    snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                } finally {
                    busy -= a.name
                }
            }
        }
    }

    confirm?.let { (a, verb) ->
        ConfirmDialog(
            title = stringResource(
                when (verb) {
                    AppVerb.STOP -> R.string.app_stop_title
                    AppVerb.REDEPLOY -> R.string.app_redeploy_title
                    else -> R.string.app_upgrade_title
                },
                a.name,
            ),
            body = stringResource(
                when (verb) {
                    AppVerb.STOP -> R.string.app_stop_body
                    AppVerb.REDEPLOY -> R.string.app_redeploy_body
                    else -> R.string.app_upgrade_body
                },
            ),
            confirmLabel = stringResource(
                when (verb) {
                    AppVerb.STOP -> R.string.app_stop
                    AppVerb.REDEPLOY -> R.string.app_redeploy
                    else -> R.string.app_upgrade
                },
            ),
            destructive = verb == AppVerb.STOP,
            onConfirm = {
                confirm = null
                doApp(a, verb)
            },
            onDismiss = { confirm = null },
        )
    }
}

/**
 * Asks before uninstalling, with "Also delete the app's data (ix-volumes)" off by default. With it
 * on, the app's name has to be typed, as that data can't be brought back.
 */
@Composable
private fun UninstallDialog(app: AppDetail, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var removeVolumes by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val ok = !removeVolumes || typed == app.name
    com.vknxworks.truectrl.ui.components.CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_uninstall_title, app.name)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (app.customApp) R.string.app_uninstall_body_custom else R.string.app_uninstall_body))
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().switchToggle(removeVolumes) { removeVolumes = it },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_uninstall_volumes), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
                        Text(stringResource(R.string.app_uninstall_volumes_sub), style = MaterialTheme.typography.bodySmall)
                    }
                    com.vknxworks.truectrl.ui.components.CtrlSwitch(removeVolumes, onCheckedChange = null)
                }
                if (removeVolumes) {
                    Text(stringResource(R.string.confirm_type, app.name), style = MaterialTheme.typography.bodyMedium)
                    com.vknxworks.truectrl.ui.components.CtrlTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(removeVolumes) }, enabled = ok) {
                Text(stringResource(R.string.app_uninstall), color = if (ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The row is the switch: one TalkBack stop announced as a switch. */
private fun Modifier.switchToggle(value: Boolean, onChange: (Boolean) -> Unit): Modifier =
    this.then(
        Modifier.toggleable(value = value, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
    )

private fun verbDone(v: AppVerb): Int = when (v) {
    AppVerb.START -> R.string.app_started
    AppVerb.STOP -> R.string.app_stopped
    AppVerb.REDEPLOY -> R.string.app_redeployed
    AppVerb.UPGRADE -> R.string.app_upgraded
}

@Composable
private fun stateLabel(state: String): String = when (state) {
    "RUNNING" -> stringResource(R.string.app_running)
    "STOPPED" -> stringResource(R.string.app_state_stopped)
    "DEPLOYING" -> stringResource(R.string.app_deploying)
    "STOPPING" -> stringResource(R.string.app_stopping)
    "CRASHED" -> stringResource(R.string.app_crashed)
    else -> state.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun stateColor(state: String): Color = when (state) {
    "CRASHED" -> Health.critical
    "DEPLOYING", "STOPPING" -> Health.warn
    "STOPPED" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

private const val MAX_LOG_LINES = 2_000

/** Live logs of one app container, following new lines as they arrive. */
@Composable
fun AppLogsScreen(app: String, container: String, service: String) {
    val c = LocalContext.current.container
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val lines = remember { mutableStateListOf<String>() }
    var follow by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(app, container, access.generation) {
        lines.clear()
        c.system.containerLogs(app, container).collect { line ->
            lines += line
            if (lines.size > MAX_LOG_LINES) lines.removeRange(0, lines.size - MAX_LOG_LINES)
        }
    }
    LaunchedEffect(lines.size, follow) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("$app · ${service.ifBlank { container.take(12) }}", actions = {
            IconButton(onClick = { follow = !follow }) {
                Icon(
                    if (follow) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    stringResource(if (follow) R.string.logs_pause else R.string.logs_follow),
                )
            }
        })
        if (!access.connected && access.error != null) ConnectionBanner()
        Box(Modifier.fillMaxSize().background(CtrlColors.Background)) {
            if (lines.isEmpty()) {
                Text(
                    stringResource(R.string.logs_waiting),
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = listState, contentPadding = PaddingValues(8.dp)) {
                    items(lines.size) { i ->
                        Text(
                            lines[i],
                            color = CtrlColors.Text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** [url] as a Uri when it's an http or https address with a host; null for anything else (intent:, file:, …). */
internal fun webUri(url: String): Uri? {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    return uri.takeIf { !it.host.isNullOrBlank() }
}
