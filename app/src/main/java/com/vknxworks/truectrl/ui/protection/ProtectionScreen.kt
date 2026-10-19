package com.vknxworks.truectrl.ui.protection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.protection.CloudKind
import com.vknxworks.truectrl.data.protection.LastRun
import com.vknxworks.truectrl.data.protection.ReplicationTask
import com.vknxworks.truectrl.data.protection.SnapshotTask
import com.vknxworks.truectrl.data.protection.TransferTask
import com.vknxworks.truectrl.data.protection.successKey
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
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.SnapshotsRoute
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

private const val POLL_MS = 15_000L
private const val DAY_MS = 24 * 60 * 60 * 1000L

/** A pending "run now" or "stop" that waits for confirmation. */
private sealed interface Pending {
    data class RunSnapshot(val task: SnapshotTask) : Pending
    data class RunReplication(val task: ReplicationTask) : Pending
    data class RunTransfer(val task: TransferTask) : Pending
    data class StopCloud(val task: TransferTask) : Pending
}

/** Snapshots, periodic snapshot tasks, replication, cloud sync, rsync and scrub schedules. */
@Composable
fun ProtectionScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.protection.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.server
    val caps = access.capabilities
    var refreshing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val snackbar = remember { SnackbarHostState() }

    PollWhileVisible(listOf(server?.id, access.generation), POLL_MS) {
        c.protection.refresh()
        c.protection.state.value.error == null
    }

    fun act(done: Int, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                snackbar.showSnackbar(context.getString(done))
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            }
        }
    }

    val canSnapTask = caps?.has("pool.snapshottask.update") == true && c.sessions.canWrite("SNAPSHOT_TASK_WRITE")
    val canRunSnap = caps?.has("pool.snapshottask.run") == true && c.sessions.canWrite("SNAPSHOT_TASK_WRITE")
    val canRepl = caps?.has("replication.run") == true && c.sessions.canWrite("REPLICATION_TASK_WRITE")
    val canCloud = caps?.has("cloudsync.sync") == true && c.sessions.canWrite("CLOUD_SYNC_WRITE")
    // core.get_methods only lists what this user may call, so having the method is the role check.
    val canRsync = caps?.has("rsynctask.run") == true && c.sessions.canWrite()
    val canScrub = caps?.has("pool.scrub.update") == true && c.sessions.canWrite("POOL_SCRUB_WRITE")
    val maxAgeMs = settings.backupMaxAgeDays * DAY_MS

    val snap = state.snapshot
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_protection))
            val err = state.error
            if (!access.connected && access.error != null) {
                ConnectionBanner()
            } else if (err != null && state.updatedAt > 0) {
                StaleBanner(state.updatedAt, err, server?.host.orEmpty()) { scope.launch { c.protection.refresh() } }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        if (!access.connected) c.sessions.reconnect()
                        c.protection.refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                if (state.updatedAt == 0L) {
                    EmptyState(
                        Icons.Outlined.Shield,
                        stringResource(if (access.loading || state.loading) R.string.overview_loading else R.string.overview_not_loaded),
                        stringResource(R.string.protection_loading_body, server?.name.orEmpty()),
                        Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                        item(key = "snapshots") {
                            Group {
                                GroupRow(
                                    stringResource(R.string.snapshots_title),
                                    stringResource(R.string.snapshots_browse),
                                    icon = Icons.Outlined.CameraAlt,
                                    showChevron = true,
                                    onClick = { nav.navigate(SnapshotsRoute(null)) },
                                )
                            }
                        }
                        item(key = "snaptasks") {
                            Group(stringResource(R.string.group_snapshot_tasks), footer = if (snap.snapshotTasks.isEmpty()) stringResource(R.string.none_set_up) else null) {
                                snap.snapshotTasks.forEach { t ->
                                    GroupRow(
                                        title = t.dataset + if (t.recursive) " " + stringResource(R.string.recursive_mark) else "",
                                        subtitle = listOfNotNull(
                                            scheduleText(t.schedule) + "  ·  " +
                                                pluralStringResource(keepPlural(t.lifetimeUnit), t.lifetimeValue, t.lifetimeValue),
                                            lastRunText(t.last),
                                            nextRunText(t.schedule, t.enabled),
                                        ).joinToString("\n"),
                                        titleColor = runColor(t.last),
                                        onClick = if (canRunSnap) ({ pending = Pending.RunSnapshot(t) }) else null,
                                        trailing = if (canSnapTask) {
                                            {
                                                Switch(checked = t.enabled, onCheckedChange = { on ->
                                                    act(if (on) R.string.task_enabled_done else R.string.task_disabled_done) { c.protection.setSnapshotTaskEnabled(t.id, on) }
                                                })
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        item(key = "replication") {
                            Group(stringResource(R.string.group_replication), footer = if (snap.replications.isEmpty()) stringResource(R.string.none_set_up) else null) {
                                snap.replications.forEach { t ->
                                    val success = snap.lastSuccess[successKey("replication", t.id)]
                                    GroupRow(
                                        title = t.name,
                                        subtitle = listOfNotNull(
                                            "${t.sources.joinToString()} → ${t.target}",
                                            lastRunText(t.last),
                                            successText(success, t.last, maxAgeMs),
                                            if (!t.enabled) stringResource(R.string.task_disabled) else null,
                                        ).joinToString("\n"),
                                        titleColor = runColor(t.last, stale = t.enabled && success != null && System.currentTimeMillis() - success > maxAgeMs),
                                        onClick = if (canRepl && t.last?.running != true) ({ pending = Pending.RunReplication(t) }) else null,
                                        trailing = if (t.last?.failed == true) ({ Icon(Icons.Filled.Error, null, tint = Health.critical) }) else null,
                                    )
                                }
                            }
                        }
                        for (kind in CloudKind.entries) {
                            val tasks = snap.transfers.filter { it.kind == kind }
                            item(key = "transfer-$kind") {
                                Group(
                                    stringResource(if (kind == CloudKind.CLOUD_SYNC) R.string.group_cloud_sync else R.string.group_rsync),
                                    footer = if (tasks.isEmpty()) stringResource(R.string.none_set_up) else null,
                                ) {
                                    tasks.forEach { t ->
                                        val success = snap.lastSuccess[successKey(t.kind.name, t.id)]
                                        val canRun = if (kind == CloudKind.CLOUD_SYNC) canCloud else canRsync
                                        GroupRow(
                                            title = t.name,
                                            subtitle = listOfNotNull(
                                                listOf(t.path, t.remote).filter { it.isNotBlank() }
                                                    .let { if (t.direction == "PULL") it.reversed() else it }.joinToString(" → "),
                                                lastRunText(t.last),
                                                successText(success, t.last, maxAgeMs),
                                                nextRunText(t.schedule, t.enabled),
                                            ).joinToString("\n"),
                                            titleColor = runColor(t.last, stale = t.enabled && success != null && System.currentTimeMillis() - success > maxAgeMs),
                                            onClick = when {
                                                t.last?.running == true && kind == CloudKind.CLOUD_SYNC && canCloud -> ({ pending = Pending.StopCloud(t) })
                                                t.last?.running != true && canRun -> ({ pending = Pending.RunTransfer(t) })
                                                else -> null
                                            },
                                            trailing = if (t.last?.failed == true) ({ Icon(Icons.Filled.Error, null, tint = Health.critical) }) else null,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "scrubs") {
                            Group(stringResource(R.string.group_scrub_tasks), footer = if (snap.scrubs.isEmpty()) stringResource(R.string.none_set_up) else null) {
                                snap.scrubs.forEach { s ->
                                    val sched = com.vknxworks.truectrl.data.protection.Schedule(s.minute, s.hour, s.dom, s.month, s.dow)
                                    GroupRow(
                                        title = s.poolName,
                                        subtitle = listOf(
                                            scheduleText(sched),
                                            pluralStringResource(R.plurals.scrub_threshold, s.thresholdDays, s.thresholdDays),
                                        ).joinToString("\n"),
                                        trailing = if (canScrub) {
                                            {
                                                Switch(checked = s.enabled, onCheckedChange = { on ->
                                                    act(if (on) R.string.task_enabled_done else R.string.task_disabled_done) { c.protection.setScrubEnabled(s.id, on) }
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
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current))
    }

    pending?.let { p ->
        val (title, body, label) = when (p) {
            is Pending.RunSnapshot -> Triple(
                stringResource(R.string.run_snapshot_title, p.task.dataset),
                stringResource(R.string.run_snapshot_body),
                stringResource(R.string.run_now),
            )
            is Pending.RunReplication -> Triple(
                stringResource(R.string.run_task_title, p.task.name),
                stringResource(R.string.run_replication_body),
                stringResource(R.string.run_now),
            )
            is Pending.RunTransfer -> Triple(
                stringResource(R.string.run_task_title, p.task.name),
                stringResource(if (p.task.kind == CloudKind.CLOUD_SYNC) R.string.run_cloud_body else R.string.run_rsync_body),
                stringResource(R.string.run_now),
            )
            is Pending.StopCloud -> Triple(
                stringResource(R.string.stop_task_title, p.task.name),
                stringResource(R.string.stop_cloud_body),
                stringResource(R.string.stop),
            )
        }
        ConfirmDialog(
            title = title,
            body = body,
            confirmLabel = label,
            destructive = p is Pending.StopCloud,
            onConfirm = {
                pending = null
                when (p) {
                    is Pending.RunSnapshot -> act(R.string.snapshot_taken) { c.protection.runSnapshotTask(p.task.id) }
                    is Pending.RunReplication -> act(R.string.task_started) { c.protection.runReplication(p.task.id) }
                    is Pending.RunTransfer -> act(R.string.task_started) { c.protection.runTransfer(p.task) }
                    is Pending.StopCloud -> act(R.string.task_stopped) { c.protection.abortCloudSync(p.task.id) }
                }
            },
            onDismiss = { pending = null },
        )
    }
}

private fun keepPlural(unit: String): Int = when (unit.uppercase()) {
    "HOUR" -> R.plurals.keep_hours
    "DAY" -> R.plurals.keep_days
    "WEEK" -> R.plurals.keep_weeks
    "MONTH" -> R.plurals.keep_months
    "YEAR" -> R.plurals.keep_years
    else -> R.plurals.keep_days
}

/** "Last success 3 days ago", shown when the latest run wasn't a success or to flag a stale backup. */
@Composable
private fun successText(success: Long?, last: LastRun?, maxAgeMs: Long): String? {
    if (success == null) return if (last?.failed == true) stringResource(R.string.task_no_success_known) else null
    val old = System.currentTimeMillis() - success > maxAgeMs
    return if (last?.succeeded == true && !old) null else stringResource(R.string.task_last_success, relativeTime(success))
}

@Composable
private fun runColor(last: LastRun?, stale: Boolean = false) = when {
    last?.failed == true -> Health.critical
    stale -> Health.warn
    else -> MaterialTheme.colorScheme.onSurface
}
