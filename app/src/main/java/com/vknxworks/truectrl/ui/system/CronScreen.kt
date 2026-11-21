package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.CronJob
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.CtrlSheet
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.protection.scheduleText
import com.vknxworks.truectrl.ui.theme.Health

/** Cron jobs: what runs when and as whom; turn one on or off, or run it now. */
@Composable
fun CronScreen() {
    val context = LocalContext.current
    val c = context.container
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val readOnly = active?.server?.readOnly == true
    val caps = access.capabilities
    val canWrite = !readOnly && caps?.hasRole("SYSTEM_CRON_WRITE") == true
    val canToggle = canWrite && caps?.has("cronjob.update") == true
    val canRun = canWrite && caps?.has("cronjob.run") == true
    var menu by remember { mutableStateOf<CronJob?>(null) }
    var run by remember { mutableStateOf<CronJob?>(null) }

    LoadScreen(
        title = stringResource(R.string.cron_title),
        icon = Icons.Outlined.Schedule,
        failedTitle = stringResource(R.string.cron_failed),
        load = { c.admin.cronJobs() },
    ) { jobs ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(
                footer = when {
                    readOnly -> stringResource(R.string.read_only_hint)
                    !canToggle && !canRun && jobs.isNotEmpty() -> stringResource(R.string.cron_not_allowed)
                    else -> null
                },
            ) {
                if (jobs.isEmpty()) GroupRow(stringResource(R.string.cron_none), stringResource(R.string.cron_none_sub))
                jobs.forEach { j ->
                    GroupRow(
                        cronName(j),
                        listOfNotNull(
                            if (j.description.isNotBlank()) j.command else null,
                            listOfNotNull(
                                scheduleText(j.schedule),
                                j.user.ifBlank { null }?.let { stringResource(R.string.cron_as_user, it) },
                                if (!j.enabled) stringResource(R.string.cron_off) else null,
                            ).joinToString("  ·  "),
                        ).joinToString("\n"),
                        Icons.Outlined.Schedule,
                        iconTint = if (j.enabled) Health.ok else Health.idle,
                        showChevron = canToggle || canRun,
                        onClick = if ((canToggle || canRun) && !busy) ({ menu = j }) else null,
                    )
                }
            }
        }

        menu?.let { j ->
            CtrlSheet(onDismissRequest = { menu = null }) {
                SheetTitle(cronName(j))
                Column(Modifier.navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp)) {
                    if (canRun) {
                        SheetAction(stringResource(R.string.cron_run), stringResource(R.string.cron_run_sub)) {
                            menu = null
                            run = j
                        }
                    }
                    if (canToggle) {
                        SheetAction(stringResource(if (j.enabled) R.string.cron_turn_off else R.string.cron_turn_on)) {
                            menu = null
                            change(context.getString(if (j.enabled) R.string.cron_turned_off else R.string.cron_turned_on)) { c.admin.setCronEnabled(j.id, !j.enabled) }
                        }
                    }
                }
            }
        }

        run?.let { j ->
            ConfirmDialog(
                title = stringResource(R.string.cron_run_title),
                body = stringResource(R.string.cron_run_body, j.command, j.user),
                confirmLabel = stringResource(R.string.cron_run),
                destructive = false,
                onConfirm = {
                    run = null
                    change(context.getString(R.string.cron_started)) { c.admin.runCron(j.id) }
                },
                onDismiss = { run = null },
            )
        }
    }
}

private fun cronName(j: CronJob): String = j.description.ifBlank { j.command }
