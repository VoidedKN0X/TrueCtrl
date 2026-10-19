package com.vknxworks.truectrl.ui.storage

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vknxworks.truectrl.data.storage.DiskInfo
import com.vknxworks.truectrl.data.storage.PoolDetail
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.LocalBottomClearance
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.StaleBanner
import com.vknxworks.truectrl.ui.components.TabTitle
import com.vknxworks.truectrl.ui.components.UsageBar
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.components.percent
import com.vknxworks.truectrl.ui.components.usageColor
import com.vknxworks.truectrl.ui.nav.DiskRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.PoolRoute
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private const val POLL_MS = 30_000L

/** Pools and disks. Datasets join this tab in a later version. */
@Composable
fun StorageScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.storage.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.server
    var section by rememberSaveable { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    PollWhileVisible(listOf(server?.id, access.generation), POLL_MS) {
        c.storage.refresh()
        c.storage.state.value.error == null
    }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(server?.id, access.generation, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { c.storage.followScans() }
    }

    val snapshot = state.snapshot
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_storage))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            listOf(R.string.storage_pools, R.string.storage_disks).forEachIndexed { i, label ->
                SegmentedButton(selected = section == i, onClick = { section = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                    Text(stringResource(label))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val err = state.error
        if (!access.connected && access.error != null) {
            ConnectionBanner()
        } else if (err != null && state.updatedAt > 0) {
            StaleBanner(state.updatedAt, err, server?.host.orEmpty()) { scope.launch { c.storage.refresh() } }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    if (!access.connected) c.sessions.reconnect()
                    c.storage.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            val bottom = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)
            when {
                state.updatedAt == 0L -> EmptyState(
                    Icons.Outlined.Storage,
                    stringResource(if (access.loading || state.loading) R.string.overview_loading else R.string.overview_not_loaded),
                    stringResource(R.string.storage_loading_body, server?.name.orEmpty()),
                    Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current),
                )
                section == 0 && snapshot.pools.isEmpty() -> EmptyState(
                    Icons.Outlined.Storage,
                    stringResource(R.string.pools_none),
                    stringResource(R.string.pools_none_body),
                    Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current),
                )
                section == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = bottom) {
                    items(snapshot.pools, key = { "pool-" + it.info.id }) { pool ->
                        PoolCard(pool, settings.poolWarnPercent) { nav.navigate(PoolRoute(pool.info.id)) }
                    }
                }
                snapshot.disks.isEmpty() -> EmptyState(
                    Icons.Outlined.Album,
                    stringResource(R.string.disks_none),
                    stringResource(R.string.disks_none_body),
                    Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = bottom) {
                    item(key = "disks") {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                snapshot.disks.forEachIndexed { i, d ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    DiskRow(d, snapshot.temps[d.name]) { nav.navigate(DiskRoute(d.name)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolCard(pool: PoolDetail, warn: Int, onClick: () -> Unit) {
    val info = pool.info
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (info.isOnline && !info.healthy) zfsColor("DEGRADED") else zfsColor(info.status), 12.dp)
                Spacer(Modifier.width(10.dp))
                Text(info.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (info.isOnline && info.healthy) stringResource(R.string.pool_healthy) else info.status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            UsageBar(info.usedFraction, usageColor(info.usedFraction, warn, accent), height = 6.dp)
            Text(
                stringResource(R.string.pool_usage, formatBytes(info.allocated), formatBytes(info.size), formatBytes(info.free)) +
                    (info.usedFraction?.let { "  ·  " + stringResource(R.string.percent, percent(it)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val data = pool.topology["data"].orEmpty()
            val layout = data.groupBy { it.type }.entries.joinToString(", ") { (type, v) -> if (v.size > 1) "${v.size} × $type" else type }
            val disks = pool.allDisks.size
            Text(
                listOfNotNull(
                    layout.takeIf { it.isNotBlank() },
                    if (disks > 0) pluralStringResource(R.plurals.disk_count, disks, disks) else null,
                    info.scan?.takeIf { it.isRunning }?.let {
                        stringResource(if (it.isScrub) R.string.pool_scrubbing else R.string.pool_resilvering, it.percentage.toInt())
                    },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiskRow(d: DiskInfo, temp: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOf(d.name, d.model).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(diskKind(d).takeIf { it.isNotBlank() }, d.pool?.let { stringResource(R.string.disk_in_pool, it) } ?: stringResource(R.string.disk_unused))
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (temp != null) {
            Spacer(Modifier.width(8.dp))
            Text(formatTemp(temp), style = MaterialTheme.typography.labelLarge, color = tempColor(temp, MaterialTheme.colorScheme.onSurface))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
