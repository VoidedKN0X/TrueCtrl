package com.vknxworks.truectrl.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.Attention
import com.vknxworks.truectrl.data.overview.PoolInfo
import com.vknxworks.truectrl.data.overview.Realtime
import com.vknxworks.truectrl.data.overview.SystemInfo
import com.vknxworks.truectrl.ui.components.ChartSeries
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.LocalBottomClearance
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.Sparkline
import com.vknxworks.truectrl.ui.components.StaleBanner
import com.vknxworks.truectrl.ui.components.TabTitle
import com.vknxworks.truectrl.ui.components.UsageBar
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.components.formatUptime
import com.vknxworks.truectrl.ui.components.percent
import com.vknxworks.truectrl.ui.components.usageColor
import com.vknxworks.truectrl.ui.nav.AlertsRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.ProtectionRoute
import com.vknxworks.truectrl.ui.nav.SharingRoute
import com.vknxworks.truectrl.ui.nav.StorageRoute
import com.vknxworks.truectrl.ui.nav.SystemRoute
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch
import java.util.Locale

private const val POLL_MS = 30_000L

/** The only summary screen: what needs attention first, then the system and its pools. */
@Composable
fun OverviewScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.overview.state.collectAsStateWithLifecycle()
    val realtime by c.overview.realtime.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.server
    var refreshing by remember { mutableStateOf(false) }

    // Poll while visible; refresh right away whenever a new connection signs in.
    PollWhileVisible(listOf(server?.id, access.generation), POLL_MS) {
        c.overview.refresh()
        c.overview.state.value.error == null
    }
    // Live CPU, memory, ARC and network while the Overview is on screen.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(server?.id, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { c.overview.followRealtime() }
    }

    val snapshot = state.snapshot
    val attention = remember(snapshot, settings.poolWarnPercent, settings.backupMaxAgeDays) { Attention.compute(snapshot, settings.poolWarnPercent, backupMaxAgeDays = settings.backupMaxAgeDays) }
    val open: (AttentionTarget) -> Unit = { target ->
        when (target) {
            AttentionTarget.ALERTS -> nav.navigate(AlertsRoute)
            AttentionTarget.STORAGE -> nav.tab(StorageRoute)
            AttentionTarget.PROTECTION -> nav.tab(ProtectionRoute)
            AttentionTarget.SHARING -> nav.tab(SharingRoute)
            AttentionTarget.SYSTEM -> nav.tab(SystemRoute)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_overview))
        val err = state.error
        if (!access.connected && access.error != null) {
            ConnectionBanner()
        } else if (err != null && state.updatedAt > 0) {
            StaleBanner(state.updatedAt, err, server?.host.orEmpty()) { scope.launch { c.overview.refresh(force = true) } }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    if (!access.connected) c.sessions.reconnect()
                    c.overview.refresh(force = true)
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (state.updatedAt == 0L) {
                EmptyState(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(if (access.loading || state.loading) R.string.overview_loading else R.string.overview_not_loaded),
                    body = stringResource(R.string.overview_loading_body, server?.name.orEmpty()),
                    modifier = Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                    item(key = "attention") { AttentionCard(attention, server?.name.orEmpty(), open) }
                    item(key = "system") {
                        SystemCard(
                            info = snapshot.info ?: access.info,
                            realtime = realtime,
                            live = access.connected,
                            memWarn = settings.poolWarnPercent,
                        )
                    }
                    if (snapshot.pools.isNotEmpty()) {
                        item(key = "pools") { PoolsCard(snapshot.pools, settings.poolWarnPercent) { nav.tab(StorageRoute) } }
                    }
                    item(key = "alerts") {
                        val active2 = snapshot.alerts.count { !it.dismissed }
                        AlertsLine(active2, snapshot.alerts.size - active2) { nav.navigate(AlertsRoute) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
private fun SystemCard(info: SystemInfo?, realtime: Realtime, live: Boolean, memWarn: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (live) Health.ok else Health.idle))
                Spacer(Modifier.width(10.dp))
                Text(
                    info?.hostname?.takeIf { it.isNotBlank() } ?: stringResource(R.string.system_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info != null && info.uptimeSeconds > 0) {
                    // Keeps counting from when it was read, so a cached value still makes sense.
                    val up = info.uptimeSeconds + if (live) (System.currentTimeMillis() - info.readAtMs).coerceAtLeast(0) / 1000 else 0L
                    Text(
                        stringResource(R.string.uptime_value, formatUptime(up)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (info != null) {
                Text(
                    listOfNotNull(
                        info.version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.tn_version, it.removePrefix("TrueNAS-")) },
                        info.model.takeIf { it.isNotBlank() }?.let { m ->
                            if (info.cores > 0) stringResource(R.string.cpu_model_cores, m, info.cores) else m
                        },
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val memTotal = realtime.memTotal.takeIf { it > 0 } ?: info?.physmem ?: 0
            val memUsed = realtime.memUsed.lastOrNull { it != null }
            if (memTotal > 0) {
                val fraction = memUsed?.let { it / memTotal }
                Meter(
                    stringResource(R.string.label_mem),
                    memUsed?.let { stringResource(R.string.used_of, formatBytes(it.toLong()), formatBytes(memTotal)) } ?: formatBytes(memTotal),
                    fraction,
                    usageColor(fraction, maxOf(memWarn, 90), accent),
                )
            }
            val facts = listOfNotNull(
                realtime.arcSize?.let { arc ->
                    realtime.arcHit?.let { stringResource(R.string.arc_size_hits, formatBytes(arc), it.toInt()) }
                        ?: stringResource(R.string.arc_size, formatBytes(arc))
                },
                realtime.cpuTemp?.let { stringResource(R.string.cpu_temp, String.format(Locale.getDefault(), "%.0f", it)) },
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (realtime.cpu.size >= 2) {
                Spacer(Modifier.height(2.dp))
                Sparkline(
                    stringResource(R.string.sparkline_cpu),
                    listOf(ChartSeries(stringResource(R.string.series_cpu), realtime.cpu)),
                    format = { "${it.toInt()}%" },
                    top = 100.0,
                )
                if (memTotal > 0) {
                    Sparkline(
                        stringResource(R.string.sparkline_mem),
                        listOf(ChartSeries(stringResource(R.string.series_used), realtime.memUsed)),
                        format = { formatBytes(it.toLong()) },
                        top = memTotal.toDouble(),
                    )
                }
                val rateTemplate = stringResource(R.string.per_second, "%s")
                Sparkline(
                    stringResource(R.string.sparkline_net),
                    listOf(
                        ChartSeries(stringResource(R.string.series_in), realtime.netIn),
                        ChartSeries(stringResource(R.string.series_out), realtime.netOut),
                    ),
                    format = { rateTemplate.replace("%s", formatBytes(it.toLong())) },
                )
            } else if (live) {
                Text(stringResource(R.string.live_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PoolsCard(pools: List<PoolInfo>, warn: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.pools_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Gutter + 4.dp, top = 8.dp),
        )
        Card(onClick = onClick) {
            Column(Modifier.padding(vertical = 4.dp)) {
                pools.forEachIndexed { i, pool ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PoolRow(pool, warn)
                }
            }
        }
    }
}

@Composable
private fun PoolRow(pool: PoolInfo, warn: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val statusColor = when {
        !pool.isOnline -> Health.critical
        !pool.healthy -> Health.warn
        else -> Health.ok
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(10.dp))
            Text(pool.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val scan = pool.scan
            val badge = when {
                scan?.isRunning == true -> stringResource(
                    if (scan.isScrub) R.string.pool_scrubbing else R.string.pool_resilvering,
                    scan.percentage.toInt(),
                )
                !pool.isOnline || !pool.healthy -> pool.status
                else -> null
            }
            if (badge != null) {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = if (pool.isOnline && pool.healthy) MaterialTheme.colorScheme.primary else statusColor)
                Spacer(Modifier.width(8.dp))
            }
            pool.usedFraction?.let { Text(stringResource(R.string.percent, percent(it)), style = MaterialTheme.typography.labelLarge) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        UsageBar(pool.usedFraction, usageColor(pool.usedFraction, warn, accent), height = 6.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.pool_usage, formatBytes(pool.allocated), formatBytes(pool.size), formatBytes(pool.free)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlertsLine(active: Int, dismissed: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter + 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOfNotNull(
                pluralStringResource(R.plurals.alerts_line, active, active),
                if (dismissed > 0) pluralStringResource(R.plurals.alerts_dismissed_line, dismissed, dismissed) else null,
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Meter(label: String, value: String, fraction: Double?, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fraction != null) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.percent, percent(fraction)), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(6.dp))
        UsageBar(fraction, color, height = 6.dp)
    }
}
