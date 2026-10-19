package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.system.NetInterface
import com.vknxworks.truectrl.data.system.NetRate
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

/** Network interfaces with their addresses and live traffic. */
@Composable
fun NetworkScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var ifaces by remember { mutableStateOf<List<NetInterface>?>(null) }
    var rates by remember { mutableStateOf<Map<String, NetRate>>(emptyMap()) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            ifaces = c.system.interfaces()
            error = null
        } catch (e: TnError) {
            error = e
        }
    }

    LaunchedEffect(server?.id, access.generation) { if (access.connected) load() }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(server?.id, access.generation, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            c.system.netRates().collect { rates = it }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.system_network))
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
            val list = ifaces
            when {
                list == null && error != null -> EmptyState(Icons.Outlined.Lan, stringResource(R.string.network_failed), errorMessage(context, error!!, server?.host.orEmpty()))
                list == null -> EmptyState(Icons.Outlined.Lan, stringResource(R.string.overview_loading), "")
                list.isEmpty() -> EmptyState(Icons.Outlined.Lan, stringResource(R.string.network_none), "")
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    list.forEach { i ->
                        val r = rates[i.name]
                        Group(i.name) {
                            GroupRow(
                                stringResource(if (i.linkUp) R.string.network_link_up else R.string.network_link_down),
                                listOfNotNull(
                                    i.description.ifBlank { null },
                                    i.type.ifBlank { null }?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
                                    r?.speedMbps?.let { stringResource(R.string.network_speed, it) },
                                    i.mtu?.let { stringResource(R.string.network_mtu, it) },
                                ).joinToString("  ·  "),
                                Icons.Outlined.Lan,
                                iconTint = if (i.linkUp) Health.ok else Health.idle,
                            )
                            GroupRow(
                                stringResource(R.string.network_addresses),
                                (i.addresses.ifEmpty { listOf(stringResource(R.string.none)) } + if (i.dhcp) listOf(stringResource(R.string.network_dhcp)) else emptyList())
                                    .joinToString("\n"),
                            )
                            if (r != null) {
                                GroupRow(
                                    stringResource(R.string.network_traffic),
                                    stringResource(R.string.network_rx_tx, rate(r.rx), rate(r.tx)),
                                )
                            }
                        }
                    }
                    Text("", Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun rate(bytesPerSecond: Double): String = stringResource(R.string.per_second, formatBytes(bytesPerSecond.toLong()))
