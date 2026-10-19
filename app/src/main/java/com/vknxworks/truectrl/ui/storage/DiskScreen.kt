package com.vknxworks.truectrl.ui.storage

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
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.storage.DiskInfo
import com.vknxworks.truectrl.data.storage.Graph
import com.vknxworks.truectrl.data.storage.GraphRange
import com.vknxworks.truectrl.data.storage.SmartAttribute
import com.vknxworks.truectrl.data.storage.SmartInfo
import com.vknxworks.truectrl.data.storage.TempStats
import com.vknxworks.truectrl.ui.components.ChartSeries
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.LineChart
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.PoolRoute
import com.vknxworks.truectrl.ui.overview.SeverityIcon
import com.vknxworks.truectrl.ui.overview.alertText
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** One disk: what it is, where it's used, its temperature, graphs and SMART health. */
@Composable
fun DiskScreen(name: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.storage.state.collectAsStateWithLifecycle()
    val overview by c.overview.state.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val disk = state.snapshot.disks.firstOrNull { it.name == name }
    val snackbar = remember { SnackbarHostState() }
    var showSerial by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(GraphRange.DAY) }
    var tempGraph by remember { mutableStateOf<Graph?>(null) }
    var ioGraph by remember { mutableStateOf<Graph?>(null) }
    var graphsLoaded by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<TempStats?>(null) }
    var attributes by remember { mutableStateOf<List<SmartAttribute>?>(null) }
    var testConfirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server?.id) { if (state.updatedAt == 0L) c.storage.refresh() }
    LaunchedEffect(name, access.generation) { stats = c.storage.tempStats(name) }
    LaunchedEffect(name, range, access.generation) {
        graphsLoaded = false
        tempGraph = c.storage.diskGraph("disktemp", name, range)
        ioGraph = c.storage.diskGraph("disk", name, range)
        graphsLoaded = true
    }

    val caps = access.capabilities
    val canTest = caps?.hasSmartApi == true && caps.has("smart.test.manual_test") && c.sessions.canWrite("FULL_ADMIN")

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(name)
        if (!access.connected && access.error != null) ConnectionBanner()
        if (disk == null) {
            EmptyState(Icons.Outlined.Album, stringResource(R.string.disk_missing), stringResource(R.string.disk_missing_body), Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                DiskFacts(disk, state.snapshot.temps[name], stats, showSerial, { showSerial = !showSerial }) { pool ->
                    state.snapshot.pools.firstOrNull { it.info.name == pool }?.let { nav.navigate(PoolRoute(it.info.id)) }
                }

                Group(stringResource(R.string.disk_graphs)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GraphRange.entries.forEach { r ->
                                FilterChip(
                                    selected = r == range,
                                    onClick = { range = r },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (r) {
                                                    GraphRange.HOUR -> R.string.range_hour
                                                    GraphRange.DAY -> R.string.range_day
                                                    GraphRange.WEEK -> R.string.range_week
                                                    GraphRange.MONTH -> R.string.range_month
                                                },
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        val t = tempGraph
                        val io = ioGraph
                        if (t != null) {
                            Text(stringResource(R.string.graph_temperature), style = MaterialTheme.typography.titleSmall)
                            LineChart(
                                t.times,
                                listOf(ChartSeries(stringResource(R.string.series_temp), t.series.values.firstOrNull().orEmpty())),
                                format = { formatTemp(it) },
                            )
                        }
                        if (io != null) {
                            Text(stringResource(R.string.graph_io), style = MaterialTheme.typography.titleSmall)
                            val rate = stringResource(R.string.per_second, "%s")
                            LineChart(
                                io.times,
                                listOf(
                                    ChartSeries(stringResource(R.string.series_read), io.series["reads"] ?: io.series.values.firstOrNull().orEmpty()),
                                    ChartSeries(stringResource(R.string.series_write), io.series["writes"] ?: io.series.values.drop(1).firstOrNull().orEmpty()),
                                ),
                                // TrueNAS reports disk I/O in KiB/s.
                                format = { rate.replace("%s", formatBytes((it * 1024).toLong())) },
                            )
                        }
                        if (t == null && io == null) {
                            Text(
                                stringResource(if (graphsLoaded) R.string.graphs_none else R.string.overview_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (caps?.hasSmartApi == true) {
                    SmartSection(
                        info = state.snapshot.smart[name],
                        attributes = attributes,
                        canTest = canTest,
                        onTest = { testConfirm = it },
                        onLoadAttributes = {
                            scope.launch {
                                attributes = try {
                                    c.storage.smartAttributes(name)
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                                    null
                                }
                            }
                        },
                    )
                } else {
                    // TrueNAS 25.10 and newer: SMART only reaches the API as alerts.
                    val related = overview.snapshot.alerts.filter { a ->
                        !a.dismissed && (a.text.contains(name) || (disk.serial.isNotBlank() && a.text.contains(disk.serial)))
                    }
                    Group(stringResource(R.string.smart_title), footer = stringResource(R.string.smart_via_alerts)) {
                        if (related.isEmpty()) {
                            GroupRow(stringResource(R.string.smart_no_alerts))
                        } else {
                            related.forEach { a ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    SeverityIcon(a.severity)
                                    Spacer(Modifier.width(12.dp))
                                    Text(alertText(a.text), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    testConfirm?.let { type ->
        ConfirmDialog(
            title = stringResource(if (type == "LONG") R.string.smart_long_title else R.string.smart_short_title, name),
            body = stringResource(if (type == "LONG") R.string.smart_long_body else R.string.smart_short_body),
            confirmLabel = stringResource(R.string.smart_run),
            destructive = false,
            onConfirm = {
                testConfirm = null
                val d = disk ?: return@ConfirmDialog
                scope.launch {
                    try {
                        c.storage.runSmartTest(d, type)
                        snackbar.showSnackbar(context.getString(R.string.smart_started))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                    }
                }
            },
            onDismiss = { testConfirm = null },
        )
    }
}

@Composable
private fun DiskFacts(
    d: DiskInfo,
    temp: Double?,
    stats: TempStats?,
    showSerial: Boolean,
    onToggleSerial: () -> Unit,
    onPool: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            GroupRow(stringResource(R.string.disk_model), d.model.ifBlank { stringResource(R.string.no_value) })
            if (d.serial.isNotBlank()) {
                GroupRow(
                    stringResource(R.string.disk_serial),
                    if (showSerial) d.serial else shortSerial(d.serial),
                    onClick = onToggleSerial,
                    trailing = { TextButton(onClick = onToggleSerial) { Text(stringResource(if (showSerial) R.string.hide_secret else R.string.show_secret)) } },
                )
            }
            GroupRow(stringResource(R.string.disk_kind), diskKind(d).ifBlank { stringResource(R.string.no_value) })
            val pool = d.pool
            if (pool != null) {
                GroupRow(stringResource(R.string.disk_pool), pool, showChevron = true, onClick = { onPool(pool) })
            } else {
                GroupRow(stringResource(R.string.disk_pool), stringResource(R.string.disk_unused))
            }
            GroupRow(
                stringResource(R.string.disk_temperature),
                listOfNotNull(
                    temp?.let(::formatTemp) ?: stringResource(R.string.disk_temp_unknown),
                    stats?.let { s ->
                        stringResource(
                            R.string.disk_temp_week,
                            s.min?.let(::formatTemp) ?: "—",
                            s.avg?.let(::formatTemp) ?: "—",
                            s.max?.let(::formatTemp) ?: "—",
                        )
                    },
                ).joinToString("\n"),
                titleColor = tempColor(temp, MaterialTheme.colorScheme.onSurface),
            )
            if (d.devname.isNotBlank() || d.bus.isNotBlank()) {
                GroupRow(stringResource(R.string.disk_device), listOf(d.devname, d.bus).filter { it.isNotBlank() }.joinToString("  ·  "))
            }
        }
    }
}

@Composable
private fun SmartSection(
    info: SmartInfo?,
    attributes: List<SmartAttribute>?,
    canTest: Boolean,
    onTest: (String) -> Unit,
    onLoadAttributes: () -> Unit,
) {
    Group(stringResource(R.string.smart_title)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tests = info?.tests.orEmpty()
            val hours = tests.mapNotNull { it.lifetime }.maxOrNull()
            info?.runningProgress?.let { Text(stringResource(R.string.smart_running, it), style = MaterialTheme.typography.titleSmall) }
            if (hours != null) Text(stringResource(R.string.smart_power_on, hours), style = MaterialTheme.typography.bodyMedium)
            if (tests.isEmpty()) {
                Text(stringResource(R.string.smart_no_tests), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tests.take(8).forEach { t ->
                    val severity = when (t.status) {
                        "SUCCESS", "RUNNING" -> null
                        "ABORTED" -> Severity.WARNING
                        else -> Severity.CRITICAL
                    }
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(t.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(t.statusText, t.lifetime?.let { stringResource(R.string.smart_at_hours, it) }).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (severity == Severity.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (canTest) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onTest("SHORT") }) { Text(stringResource(R.string.smart_short)) }
                    OutlinedButton(onClick = { onTest("LONG") }) { Text(stringResource(R.string.smart_long)) }
                }
            }
            if (attributes == null) {
                TextButton(onClick = onLoadAttributes) { Text(stringResource(R.string.smart_show_attributes)) }
            } else if (attributes.isEmpty()) {
                Text(stringResource(R.string.smart_no_attributes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                attributes.forEach { a ->
                    Row {
                        Text(listOfNotNull(a.id?.toString(), a.name).joinToString(" "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(a.raw, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
