package com.vknxworks.truectrl.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.vknxworks.truectrl.ui.components.CtrlCard
import com.vknxworks.truectrl.ui.components.CtrlHeroCard
import com.vknxworks.truectrl.ui.components.CtrlLabel
import com.vknxworks.truectrl.ui.components.CtrlListRow
import com.vknxworks.truectrl.ui.components.CtrlMetricCard
import com.vknxworks.truectrl.ui.components.CtrlSectionHeader
import com.vknxworks.truectrl.ui.components.CtrlTopBar
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlType

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
        CtrlTopBar(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 4.dp))
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
                    item(key = "headline") { Headline(snapshot.info ?: access.info, server?.name.orEmpty(), access.connected) }
                    item(key = "system") {
                        SystemCard(
                            info = snapshot.info ?: access.info,
                            realtime = realtime,
                            pools = snapshot.pools,
                            live = access.connected,
                            memWarn = settings.poolWarnPercent,
                            poolWarn = settings.poolWarnPercent,
                            onStorage = { nav.tab(StorageRoute) },
                        )
                    }
                    item(key = "attention") { AttentionCard(attention, server?.name.orEmpty(), open) }
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

/** Node name as the big headline, with its state above and version and uptime below. */
@Composable
private fun Headline(info: SystemInfo?, serverName: String, live: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CtrlLabel(stringResource(if (live) R.string.overview_state_online else R.string.overview_state_offline))
        Text(
            info?.hostname?.takeIf { it.isNotBlank() } ?: serverName.ifBlank { stringResource(R.string.system_title) },
            style = MaterialTheme.typography.displayLarge,
            color = CtrlColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (info != null) {
            // Keeps counting from when it was read, so a cached value still makes sense.
            val up = info.uptimeSeconds + if (live) (System.currentTimeMillis() - info.readAtMs).coerceAtLeast(0) / 1000 else 0L
            val meta = listOfNotNull(
                info.version.takeIf { it.isNotBlank() }?.removePrefix("TrueNAS-"),
                if (info.uptimeSeconds > 0) stringResource(R.string.overview_uptime_short, formatUptime(up)) else null,
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The live system block: the CPU hero card with its recent load as bars, memory and storage
 * metric cards, then the trend lines (memory, network) and ARC / temperature facts.
 */
@Composable
private fun SystemCard(
    info: SystemInfo?,
    realtime: Realtime,
    pools: List<PoolInfo>,
    live: Boolean,
    memWarn: Int,
    poolWarn: Int,
    onStorage: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val cpuNow = realtime.cpu.lastOrNull { it != null }
        CtrlHeroCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtrlLabel(stringResource(R.string.overview_cpu_load), Modifier.weight(1f), color = CtrlColors.OnAccent)
                if (info != null && info.cores > 0) {
                    CtrlLabel(pluralStringResource(R.plurals.overview_threads, info.cores, info.cores), color = CtrlColors.OnAccent)
                }
            }
            Text(
                buildAnnotatedString {
                    append(cpuNow?.let { it.coerceIn(0.0, 100.0).roundToInt().toString() } ?: "—")
                    withStyle(SpanStyle(fontSize = 28.sp, letterSpacing = 0.sp)) { append("%") }
                },
                style = CtrlType.BigNumber,
                color = CtrlColors.OnAccent,
            )
            if (realtime.cpu.size >= 2) {
                LoadBars(realtime.cpu, Modifier.fillMaxWidth().height(34.dp))
            } else if (live) {
                Text(stringResource(R.string.live_waiting), style = CtrlType.Mono, color = CtrlColors.OnAccent)
            }
            if (info != null && info.model.isNotBlank()) {
                Text(info.model, style = CtrlType.Mono, color = CtrlColors.OnAccent.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        val memTotal = realtime.memTotal.takeIf { it > 0 } ?: info?.physmem ?: 0
        val memUsed = realtime.memUsed.lastOrNull { it != null }
        val memFraction = if (memTotal > 0) memUsed?.let { it / memTotal } else null
        val poolSize = pools.sumOf { it.size }
        val poolUsed = pools.sumOf { it.allocated }
        val poolFraction = if (poolSize > 0) poolUsed.toDouble() / poolSize else null
        if (memTotal > 0 || poolSize > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlMetricCard(
                    label = stringResource(R.string.overview_memory),
                    value = memUsed?.let { formatBytes(it.toLong()) } ?: formatBytes(memTotal),
                    unit = if (memUsed != null && memTotal > 0) stringResource(R.string.overview_of_total, formatBytes(memTotal)) else null,
                    fraction = memFraction ?: if (memTotal > 0) 0.0 else null,
                    barColor = usageColor(memFraction, maxOf(memWarn, 90), CtrlColors.Text),
                    modifier = Modifier.weight(1f),
                )
                CtrlMetricCard(
                    label = stringResource(R.string.overview_storage),
                    value = if (poolSize > 0) formatBytes(poolUsed) else "—",
                    unit = if (poolSize > 0) stringResource(R.string.overview_of_total, formatBytes(poolSize)) else null,
                    fraction = poolFraction ?: if (poolSize > 0) 0.0 else null,
                    barColor = usageColor(poolFraction, poolWarn, CtrlColors.Text),
                    modifier = Modifier.weight(1f),
                    onClick = onStorage,
                )
            }
        }

        val facts = listOfNotNull(
            realtime.arcSize?.let { arc ->
                realtime.arcHit?.let { stringResource(R.string.arc_size_hits, formatBytes(arc), it.toInt()) }
                    ?: stringResource(R.string.arc_size, formatBytes(arc))
            },
            realtime.cpuTemp?.let { stringResource(R.string.cpu_temp, String.format(Locale.getDefault(), "%.0f", it)) },
        )
        if (realtime.cpu.size >= 2 || facts.isNotEmpty()) {
            CtrlCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (realtime.cpu.size >= 2) {
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
                    }
                    if (facts.isNotEmpty()) {
                        Text(facts.joinToString("  ·  "), style = CtrlType.Mono, color = CtrlColors.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The CPU history as thin bars on the hero card; the newest few are solid. */
@Composable
private fun LoadBars(values: List<Double?>, modifier: Modifier) {
    val shown = values.takeLast(24)
    Canvas(modifier) {
        val n = shown.size
        if (n == 0) return@Canvas
        val gap = 4.dp.toPx()
        val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        shown.forEachIndexed { i, v ->
            val f = ((v ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
            val h = (size.height * f).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = CtrlColors.OnAccent.copy(alpha = if (i >= n - 4) 1f else 0.55f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = r,
            )
        }
    }
}

@Composable
private fun PoolsCard(pools: List<PoolInfo>, warn: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        val healthy = pools.count { it.isOnline && it.healthy }
        CtrlSectionHeader(stringResource(R.string.pools_title), trailing = stringResource(R.string.overview_pools_healthy, healthy, pools.size))
        pools.forEach { pool -> PoolRow(pool, warn, onClick) }
    }
}

@Composable
private fun PoolRow(pool: PoolInfo, warn: Int, onClick: () -> Unit) {
    val statusColor = when {
        !pool.isOnline -> Health.critical
        !pool.healthy -> Health.warn
        else -> CtrlColors.Accent
    }
    val scan = pool.scan
    val status = when {
        scan?.isRunning == true -> stringResource(
            if (scan.isScrub) R.string.pool_scrubbing else R.string.pool_resilvering,
            scan.percentage.toInt(),
        )
        else -> pool.status
    }
    CtrlListRow(
        title = pool.name,
        leading = pool.usedFraction?.let { stringResource(R.string.percent, percent(it)) } ?: "—",
        subtitle = stringResource(R.string.pool_usage, formatBytes(pool.allocated), formatBytes(pool.size), formatBytes(pool.free)),
        status = status,
        statusColor = statusColor,
        onClick = onClick,
    ) {
        UsageBar(pool.usedFraction, usageColor(pool.usedFraction, warn, CtrlColors.Text), height = 6.dp)
    }
}

@Composable
private fun AlertsLine(active: Int, dismissed: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        CtrlListRow(
            title = listOfNotNull(
                pluralStringResource(R.plurals.alerts_line, active, active),
                if (dismissed > 0) pluralStringResource(R.plurals.alerts_dismissed_line, dismissed, dismissed) else null,
            ).joinToString("  ·  "),
            status = if (active > 0) active.toString() else null,
            statusColor = if (active > 0) Health.warn else CtrlColors.Inactive,
            onClick = onClick,
        )
    }
}
