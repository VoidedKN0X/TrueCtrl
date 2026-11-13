package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.system.AppDetail
import com.vknxworks.truectrl.data.system.AppUpdates
import com.vknxworks.truectrl.data.system.AppUpgrade
import com.vknxworks.truectrl.data.system.UpgradeState
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.CtrlButton
import com.vknxworks.truectrl.ui.components.CtrlOutlinedButton
import com.vknxworks.truectrl.ui.components.CtrlProgressBar
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health

/**
 * "Update all apps": the installed apps with an update, all ticked to start with. Update the ticked ones
 * (or all) after a confirmation; each app's upgrade job is followed with its progress, one app after
 * another, and the run goes on when this screen is left.
 */
@Composable
fun AppUpdatesScreen() {
    val context = LocalContext.current
    val c = context.container
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val run by c.appUpdates.run.collectAsStateWithLifecycle()
    val server = active?.server
    var apps by remember { mutableStateOf<List<AppDetail>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    // Unticked apps; everything else is ticked, including apps that get an update while the screen is open.
    var unticked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    val canUpgrade = c.system.canUpgradeApps()
    val running = run?.running == true
    val thisServerRun = run?.takeIf { it.serverId == server?.id }

    suspend fun load(): Boolean = try {
        apps = c.system.apps()
        error = null
        true
    } catch (e: TnError) {
        error = e
        false
    }

    // While a run is going, reloading would only show half-updated versions; refresh again once it's done.
    PollWhileVisible(listOf(server?.id, access.generation, running), 15_000L) { if (access.connected && !running) load() else false }

    fun start(names: List<String>) {
        val list = apps ?: return
        val plan = AppUpdates.plan(list, names.toSet())
        val (serverId, upgrader) = c.system.upgrader() ?: return
        c.appUpdates.start(serverId, plan, upgrader)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.apps_update_all))
        if (!access.connected && access.error != null) ConnectionBanner()
        val list = apps?.let(AppUpdates::upgradable)
        when {
            thisServerRun != null -> RunView(
                items = thisServerRun.items,
                running = thisServerRun.running,
                stopping = thisServerRun.stopping,
                progress = thisServerRun.progress,
                done = thisServerRun.done,
                failed = thisServerRun.failed,
                skipped = thisServerRun.skipped,
                host = server?.host.orEmpty(),
                onStop = { c.appUpdates.stop() },
                onDone = { c.appUpdates.clear() },
            )
            list == null && error != null -> EmptyState(Icons.Outlined.Upgrade, stringResource(R.string.apps_failed), errorMessage(context, error!!, server?.host.orEmpty()))
            list == null -> EmptyState(Icons.Outlined.Upgrade, stringResource(R.string.overview_loading), "")
            list.isEmpty() -> EmptyState(Icons.Outlined.Upgrade, stringResource(R.string.apps_update_none), stringResource(R.string.apps_update_none_body))
            else -> {
                val ticked = list.map { it.name }.filter { it !in unticked }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Text(
                            stringResource(R.string.apps_update_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                        )
                    }
                    item {
                        Group(footer = when {
                            !canUpgrade -> stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.apps_role_hint)
                            running -> stringResource(R.string.apps_update_other_running)
                            else -> null
                        }) {
                            list.forEach { app ->
                                val on = app.name !in unticked
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .toggleable(value = on, enabled = canUpgrade, role = Role.Checkbox) {
                                            unticked = if (it) unticked - app.name else unticked + app.name
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = on, onCheckedChange = null, enabled = canUpgrade)
                                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                                        Text(app.name, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
                                        Text(
                                            stringResource(R.string.apps_update_versions, app.version.ifBlank { "?" }, app.latestVersion ?: "?"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (canUpgrade) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CtrlOutlinedButton(
                            onClick = { confirm = ticked },
                            enabled = !running && ticked.isNotEmpty() && ticked.size < list.size,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.apps_update_selected, ticked.size)) }
                        CtrlButton(
                            onClick = { confirm = list.map { it.name } },
                            enabled = !running,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.apps_update_all_button, list.size)) }
                    }
                }
            }
        }
    }

    confirm?.let { names ->
        ConfirmDialog(
            title = pluralStringResource(R.plurals.apps_update_confirm_title, names.size, names.size),
            body = stringResource(R.string.apps_update_confirm_body, names.joinToString(", ")),
            confirmLabel = stringResource(R.string.apps_update_confirm),
            destructive = false,
            onConfirm = {
                confirm = null
                start(names)
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun RunView(
    items: List<AppUpgrade>,
    running: Boolean,
    stopping: Boolean,
    progress: Float,
    done: Int,
    failed: Int,
    skipped: Int,
    host: String,
    onStop: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
            Text(
                if (running) {
                    stringResource(if (stopping) R.string.apps_update_stopping else R.string.apps_update_running, done + failed, items.size)
                } else {
                    stringResource(R.string.apps_update_finished, done, failed, skipped)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            CtrlProgressBar(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Group {
                    items.forEach { a ->
                        GroupRow(
                            title = a.name,
                            subtitle = when (a.state) {
                                UpgradeState.QUEUED -> stringResource(R.string.apps_update_queued)
                                UpgradeState.RUNNING -> listOfNotNull(
                                    a.percent?.let { "${it.toInt()} %" },
                                    a.progressText ?: stringResource(R.string.apps_update_updating),
                                ).joinToString("  ·  ")
                                UpgradeState.DONE -> stringResource(R.string.apps_update_done, a.to ?: "")
                                UpgradeState.FAILED -> a.error?.let { errorMessage(context, it, host) } ?: stringResource(R.string.apps_update_failed)
                                UpgradeState.SKIPPED -> stringResource(R.string.apps_update_skipped)
                            },
                            icon = when (a.state) {
                                UpgradeState.QUEUED -> Icons.Outlined.Schedule
                                UpgradeState.RUNNING -> null
                                UpgradeState.DONE -> Icons.Outlined.CheckCircle
                                UpgradeState.FAILED -> Icons.Outlined.ErrorOutline
                                UpgradeState.SKIPPED -> Icons.Outlined.RemoveCircleOutline
                            },
                            iconTint = when (a.state) {
                                UpgradeState.DONE -> Health.ok
                                UpgradeState.FAILED -> Health.critical
                                else -> CtrlColors.Muted
                            },
                            trailing = if (a.state == UpgradeState.RUNNING) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (running) {
                CtrlOutlinedButton(onClick = onStop, enabled = !stopping) { Text(stringResource(R.string.apps_update_stop)) }
            } else {
                CtrlButton(onClick = onDone) { Text(stringResource(R.string.apps_update_close)) }
            }
        }
    }
}
