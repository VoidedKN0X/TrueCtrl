package com.vknxworks.truectrl.ui.system

import com.vknxworks.truectrl.ui.components.CtrlProgressBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkHistory
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.system.JobInfo
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch

/** Recent TrueNAS jobs with live progress; running ones that allow it can be stopped. */
@Composable
fun JobsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var jobs by remember { mutableStateOf<List<JobInfo>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var abort by remember { mutableStateOf<JobInfo?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // Stopping a job is a change: not in read-only mode.
    val canAbort = server?.readOnly == false

    suspend fun load(): Boolean = try {
        jobs = c.system.jobs()
        error = null
        true
    } catch (e: TnError) {
        error = e
        false
    }

    // Faster while something runs, so progress moves.
    val anyActive = jobs?.any { it.active } == true
    PollWhileVisible(listOf(server?.id, access.generation, anyActive), if (anyActive) 2_000L else 10_000L) {
        if (access.connected) load() else false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.system_jobs))
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
            val list = jobs
            when {
                list == null && error != null -> EmptyState(Icons.Outlined.WorkHistory, stringResource(R.string.jobs_failed), errorMessage(context, error!!, server?.host.orEmpty()))
                list == null -> EmptyState(Icons.Outlined.WorkHistory, stringResource(R.string.overview_loading), "")
                list.isEmpty() -> EmptyState(Icons.Outlined.WorkHistory, stringResource(R.string.jobs_none), stringResource(R.string.jobs_none_body))
                else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    val (running, done) = list.partition { it.active }
                    if (running.isNotEmpty()) {
                        item(key = "running") {
                            Group(stringResource(R.string.jobs_running)) { running.forEach { j -> JobRow(j, canAbort) { abort = j } } }
                        }
                    }
                    if (done.isNotEmpty()) {
                        item(key = "done") {
                            Group(stringResource(R.string.jobs_recent)) { done.forEach { j -> JobRow(j, false) {} } }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    abort?.let { j ->
        ConfirmDialog(
            title = stringResource(R.string.job_abort_title),
            body = stringResource(R.string.job_abort_body, jobName(j)),
            confirmLabel = stringResource(R.string.job_abort),
            destructive = true,
            onConfirm = {
                abort = null
                scope.launch {
                    try {
                        c.system.abortJob(j.id)
                        load()
                        snackbar.showSnackbar(context.getString(R.string.job_aborted))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                    }
                }
            },
            onDismiss = { abort = null },
        )
    }
}

private fun jobName(j: JobInfo): String = j.description ?: j.method

@Composable
private fun JobRow(j: JobInfo, canAbort: Boolean, onAbort: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        GroupRow(
            title = jobName(j),
            subtitle = listOfNotNull(
                if (j.description != null) j.method else null,
                when (j.state) {
                    "SUCCESS" -> stringResource(R.string.task_ok)
                    "FAILED" -> stringResource(R.string.task_failed) + (j.error?.let { ": $it" } ?: "")
                    "ABORTED" -> stringResource(R.string.job_state_aborted)
                    "WAITING" -> stringResource(R.string.job_state_waiting)
                    "RUNNING" -> listOfNotNull(
                        j.percent?.let { stringResource(R.string.task_running_pct, it.toInt()) } ?: stringResource(R.string.task_running),
                        j.progressText,
                    ).joinToString(": ")
                    else -> j.state
                },
                (j.finishedMs ?: j.startedMs)?.let { relativeTime(it) },
            ).joinToString("\n"),
            titleColor = if (j.state == "FAILED") Health.critical else MaterialTheme.colorScheme.onSurface,
            trailing = if (canAbort && j.abortable && j.active) ({ TextButton(onClick = onAbort) { Text(stringResource(R.string.job_abort)) } }) else null,
        )
        if (j.active) {
            val p = j.percent
            if (p != null) {
                CtrlProgressBar(progress = { (p / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            } else {
                CtrlProgressBar(progress = null, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}
