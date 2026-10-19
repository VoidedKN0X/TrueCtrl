package com.vknxworks.truectrl.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.overview.AlertInfo
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Every TrueNAS alert: active ones first (most serious, then newest), then dismissed ones. Tap one
 * for the full text, and to dismiss or restore it when this user may.
 */
@Composable
fun AlertsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val state by c.overview.state.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var open by remember { mutableStateOf<AlertInfo?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val canChange = server?.readOnly == false && access.capabilities?.hasRole("ALERT_LIST_WRITE") == true

    val sorted = remember(state.snapshot.alerts) {
        state.snapshot.alerts.sortedWith(
            compareBy<AlertInfo> { it.dismissed }.thenBy { it.severity.ordinal }.thenByDescending { it.atMs ?: 0 },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.alerts_title))
        if (!access.connected && access.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    c.overview.refresh(force = true)
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (sorted.isEmpty()) {
                EmptyState(
                    Icons.Outlined.NotificationsNone,
                    stringResource(R.string.alerts_none),
                    stringResource(R.string.alerts_none_body, server?.name.orEmpty()),
                    Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(sorted, key = { it.uuid }) { alert -> AlertRow(alert) { open = alert } }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    open?.let { alert ->
        AlertDialog(
            onDismissRequest = { open = null },
            icon = { SeverityIcon(alert.severity) },
            title = { Text(stringResource(severityLabel(alert.severity))) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(alertText(alert.text)) }
                    Spacer(Modifier.padding(top = 12.dp))
                    Text(
                        listOfNotNull(
                            alert.atMs?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
                            alert.klass.takeIf { it.isNotBlank() },
                            if (alert.dismissed) stringResource(R.string.alert_dismissed) else null,
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                if (canChange) {
                    // Dismissing can always be undone with Restore, so it needs no confirmation.
                    TextButton(onClick = {
                        open = null
                        scope.launch {
                            try {
                                c.overview.setDismissed(alert.uuid, !alert.dismissed)
                            } catch (e: Exception) {
                                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                            }
                        }
                    }) { Text(stringResource(if (alert.dismissed) R.string.alert_restore else R.string.alert_dismiss)) }
                } else {
                    TextButton(onClick = { open = null }) { Text(stringResource(R.string.close)) }
                }
            },
            dismissButton = if (canChange) {
                { TextButton(onClick = { open = null }) { Text(stringResource(R.string.close)) } }
            } else {
                null
            },
        )
    }
}

fun severityLabel(severity: Severity): Int = when (severity) {
    Severity.CRITICAL -> R.string.sev_critical
    Severity.WARNING -> R.string.sev_warning
    Severity.INFO -> R.string.sev_info
}

@Composable
private fun AlertRow(alert: AlertInfo, onClick: () -> Unit) {
    val text = alertText(alert.text)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SeverityIcon(alert.severity)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (alert.dismissed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    alert.atMs?.let { relativeTime(it) },
                    if (alert.dismissed) stringResource(R.string.alert_dismissed) else null,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
