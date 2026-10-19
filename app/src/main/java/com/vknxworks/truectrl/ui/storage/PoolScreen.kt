package com.vknxworks.truectrl.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.ScanInfo
import com.vknxworks.truectrl.data.overview.ScrubSchedule
import com.vknxworks.truectrl.data.storage.Cron
import com.vknxworks.truectrl.data.storage.PoolDetail
import com.vknxworks.truectrl.data.storage.ScrubAction
import com.vknxworks.truectrl.data.storage.Vdev
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.UsageBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.components.formatUptime
import com.vknxworks.truectrl.ui.components.percent
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.components.usageColor
import com.vknxworks.truectrl.ui.nav.DiskRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** One pool: health, space, scrubs (with start, pause and stop) and its layout down to each disk. */
@Composable
fun PoolScreen(poolId: Int) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.storage.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val pool = state.snapshot.pools.firstOrNull { it.info.id == poolId }
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<ScrubAction?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val canScrub = access.capabilities?.has("pool.scrub") == true && c.sessions.canWrite("POOL_WRITE")

    LaunchedEffect(server?.id) { if (state.updatedAt == 0L) c.storage.refresh() }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(server?.id, access.generation, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { c.storage.followScans() }
    }

    fun act(action: ScrubAction) {
        busy = true
        scope.launch {
            try {
                c.storage.scrub(poolId, action)
                snackbar.showSnackbar(
                    context.getString(
                        when (action) {
                            ScrubAction.START -> R.string.scrub_started
                            ScrubAction.PAUSE -> R.string.scrub_paused
                            ScrubAction.STOP -> R.string.scrub_stopped
                        },
                    ),
                )
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(pool?.info?.name ?: stringResource(R.string.pool_title))
        if (!access.connected && access.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    c.storage.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (pool == null) {
                EmptyState(Icons.Outlined.Storage, stringResource(R.string.pool_missing), stringResource(R.string.pool_missing_body), Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    StatusCard(pool, settings.poolWarnPercent)
                    ScrubCard(
                        pool = pool,
                        schedule = state.snapshot.scrubs.firstOrNull { it.poolId == poolId || it.poolName == pool.info.name },
                        canScrub = canScrub && !busy,
                        readOnly = server?.readOnly == true,
                        onAction = { confirm = it },
                    )
                    PoolDetail.GROUPS.forEach { group ->
                        val vdevs = pool.topology[group].orEmpty()
                        if (vdevs.isNotEmpty()) {
                            Group(groupLabel(group)) {
                                vdevs.forEachIndexed { i, v ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    VdevBlock(v) { disk -> nav.navigate(DiskRoute(disk)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    confirm?.let { action ->
        val name = pool?.info?.name.orEmpty()
        ConfirmDialog(
            title = stringResource(
                when (action) {
                    ScrubAction.START -> R.string.scrub_start_title
                    ScrubAction.PAUSE -> R.string.scrub_pause_title
                    ScrubAction.STOP -> R.string.scrub_stop_title
                },
                name,
            ),
            body = stringResource(
                when (action) {
                    ScrubAction.START -> R.string.scrub_start_body
                    ScrubAction.PAUSE -> R.string.scrub_pause_body
                    ScrubAction.STOP -> R.string.scrub_stop_body
                },
            ),
            confirmLabel = stringResource(
                when (action) {
                    ScrubAction.START -> R.string.scrub_start
                    ScrubAction.PAUSE -> R.string.scrub_pause
                    ScrubAction.STOP -> R.string.scrub_stop
                },
            ),
            destructive = action == ScrubAction.STOP,
            onConfirm = {
                confirm = null
                act(action)
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun StatusCard(pool: PoolDetail, warn: Int) {
    val info = pool.info
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (info.isOnline && !info.healthy) zfsColor("DEGRADED") else zfsColor(info.status), 12.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (info.isOnline && info.healthy) stringResource(R.string.pool_online_healthy) else info.status,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                info.usedFraction?.let { Text(stringResource(R.string.percent, percent(it)), style = MaterialTheme.typography.labelLarge) }
            }
            info.statusDetail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            UsageBar(info.usedFraction, usageColor(info.usedFraction, warn, accent), height = 6.dp)
            Text(
                stringResource(R.string.pool_usage, formatBytes(info.allocated), formatBytes(info.size), formatBytes(info.free)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val facts = listOfNotNull(
                info.fragmentation?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.pool_fragmentation, it.trimEnd('%')) },
                pool.compressRatio?.let { stringResource(R.string.pool_compression, it) },
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pool.isUpgraded == false) {
                Text(stringResource(R.string.pool_not_upgraded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScrubCard(pool: PoolDetail, schedule: ScrubSchedule?, canScrub: Boolean, readOnly: Boolean, onAction: (ScrubAction) -> Unit) {
    val scan: ScanInfo? = pool.info.scan
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Group(stringResource(R.string.scrub_title)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val running = scan != null && scan.state == "SCANNING"
            if (running && scan != null) {
                Text(
                    stringResource(
                        when {
                            scan.paused -> R.string.scan_paused
                            scan.isScrub -> R.string.scan_scrubbing
                            else -> R.string.scan_resilvering
                        },
                        scan.percentage.toInt(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                LinearProgressIndicator(progress = { (scan.percentage / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    listOfNotNull(
                        scan.startMs?.let { stringResource(R.string.scan_started, relativeTime(it)) },
                        scan.secondsLeft?.takeIf { it > 0 && !scan.paused }?.let { stringResource(R.string.scan_left, formatUptime(it)) },
                        if (scan.errors > 0) stringResource(R.string.scan_errors, scan.errors) else null,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val last = scan?.takeIf { it.isScrub }
                if (last?.endMs != null) {
                    val duration = last.startMs?.let { (last.endMs - it) / 1000 }
                    Text(
                        stringResource(
                            if (last.state == "CANCELED") R.string.scrub_last_canceled else R.string.scrub_last,
                            dateFormat.format(Date(last.endMs)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        listOfNotNull(
                            duration?.takeIf { it > 0 }?.let { stringResource(R.string.scrub_took, formatUptime(it)) },
                            if (last.errors > 0) stringResource(R.string.scan_errors, last.errors) else stringResource(R.string.scrub_no_errors),
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (last.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.scrub_never), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(nextScrubText(schedule, scan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (canScrub) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!running || scan?.paused == true) {
                        OutlinedButton(onClick = { onAction(ScrubAction.START) }) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (scan?.paused == true) R.string.scrub_resume else R.string.scrub_start))
                        }
                    }
                    if (running && scan?.isScrub == true && !scan.paused) {
                        OutlinedButton(onClick = { onAction(ScrubAction.PAUSE) }) {
                            Icon(Icons.Filled.Pause, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scrub_pause))
                        }
                    }
                    if (running && scan?.isScrub == true) {
                        OutlinedButton(onClick = { onAction(ScrubAction.STOP) }) {
                            Icon(Icons.Filled.Stop, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scrub_stop))
                        }
                    }
                }
            } else {
                Text(
                    stringResource(if (readOnly) R.string.read_only_active else R.string.scrub_not_allowed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * When the next scheduled scrub runs: the first time the schedule fires after the threshold has
 * passed since the last scrub (TrueNAS skips scheduled runs until then).
 */
@Composable
private fun nextScrubText(schedule: ScrubSchedule?, scan: ScanInfo?): String {
    if (schedule == null) return stringResource(R.string.scrub_no_schedule)
    if (!schedule.enabled) return stringResource(R.string.scrub_schedule_off)
    val lastEnd = scan?.takeIf { it.isScrub }?.endMs ?: 0L
    val earliest = maxOf(System.currentTimeMillis(), lastEnd + schedule.thresholdDays * DAY_MS)
    val next = Cron(schedule.minute, schedule.hour, schedule.dom, schedule.month, schedule.dow).nextAfter(earliest)
        ?: return stringResource(R.string.scrub_schedule_unknown)
    val f = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    return stringResource(R.string.scrub_next, f.format(Date(next)), schedule.thresholdDays)
}

/** A vdev with its state and errors, and the disks inside it. */
@Composable
private fun VdevBlock(v: Vdev, onDisk: (String) -> Unit) {
    if (v.children.isEmpty()) {
        DiskLine(v, indent = false, onDisk)
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(zfsColor(v.status))
            Spacer(Modifier.width(10.dp))
            Text(v.type, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(v.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        v.children.forEach { child ->
            if (child.children.isNotEmpty()) {
                // A spare or replacement in progress inside a vdev: show its disks too.
                child.disks().forEach { DiskLine(it, indent = true, onDisk) }
            } else {
                DiskLine(child, indent = true, onDisk)
            }
        }
    }
}

@Composable
private fun DiskLine(d: Vdev, indent: Boolean, onDisk: (String) -> Unit) {
    val disk = d.disk
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (disk != null) Modifier.clickable { onDisk(disk) } else Modifier)
            .padding(start = if (indent) 38.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(zfsColor(d.status), 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(disk ?: d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    d.status,
                    if (d.errors > 0) stringResource(R.string.vdev_errors, d.readErrors, d.writeErrors, d.checksumErrors)
                    else stringResource(R.string.vdev_no_errors),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = if (d.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (disk != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
