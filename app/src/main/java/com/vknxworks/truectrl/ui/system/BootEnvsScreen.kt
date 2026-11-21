package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.MaterialTheme
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
import com.vknxworks.truectrl.data.system.BootEnv
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.CtrlSheet
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.theme.Health
import java.text.DateFormat
import java.util.Date

/**
 * Boot environments: every TrueNAS version still installed on the boot pool. Pick the one for the
 * next boot, protect one from the updater's clean-up, or delete one (after typing its name).
 */
@Composable
fun BootEnvsScreen() {
    val context = LocalContext.current
    val c = context.container
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val readOnly = active?.server?.readOnly == true
    val caps = access.capabilities
    val canWrite = !readOnly && caps?.hasRole("BOOT_ENV_WRITE") == true
    val canActivate = canWrite && caps?.has("boot.environment.activate") == true
    val canKeep = canWrite && caps?.has("boot.environment.keep") == true
    val canDestroy = canWrite && caps?.has("boot.environment.destroy") == true
    var menu by remember { mutableStateOf<BootEnv?>(null) }
    var activate by remember { mutableStateOf<BootEnv?>(null) }
    var destroy by remember { mutableStateOf<BootEnv?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LoadScreen(
        title = stringResource(R.string.bootenv_title),
        icon = Icons.Outlined.Layers,
        failedTitle = stringResource(R.string.bootenv_failed),
        load = { c.admin.bootEnvs() },
    ) { envs ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(
                footer = when {
                    readOnly -> stringResource(R.string.read_only_hint)
                    !canActivate && !canKeep && !canDestroy -> stringResource(R.string.bootenv_not_allowed)
                    else -> stringResource(R.string.bootenv_footer)
                },
            ) {
                if (envs.isEmpty()) GroupRow(stringResource(R.string.bootenv_none))
                envs.forEach { e ->
                    val anyAction = (canActivate && e.canActivate && !e.activated) || canKeep || (canDestroy && !e.active && !e.activated)
                    GroupRow(
                        e.id,
                        listOfNotNull(
                            when {
                                e.active && e.activated -> stringResource(R.string.bootenv_running_and_next)
                                e.active -> stringResource(R.string.bootenv_running)
                                e.activated -> stringResource(R.string.bootenv_next)
                                else -> null
                            },
                            e.createdMs?.let { dateFormat.format(Date(it)) },
                            formatBytes(e.usedBytes),
                            if (e.keep) stringResource(R.string.bootenv_kept) else null,
                        ).joinToString("  ·  "),
                        Icons.Outlined.Layers,
                        iconTint = if (e.active || e.activated) Health.ok else Health.idle,
                        showChevron = anyAction,
                        onClick = if (anyAction && !busy) ({ menu = e }) else null,
                    )
                }
            }
        }

        menu?.let { e ->
            CtrlSheet(onDismissRequest = { menu = null }) {
                SheetTitle(e.id)
                Column(Modifier.navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp)) {
                    if (canActivate && e.canActivate && !e.activated) {
                        SheetAction(stringResource(R.string.bootenv_activate), stringResource(R.string.bootenv_activate_sub)) {
                            menu = null
                            activate = e
                        }
                    }
                    if (canKeep) {
                        SheetAction(
                            stringResource(if (e.keep) R.string.bootenv_unkeep else R.string.bootenv_keep),
                            stringResource(if (e.keep) R.string.bootenv_unkeep_sub else R.string.bootenv_keep_sub),
                        ) {
                            menu = null
                            change(context.getString(if (e.keep) R.string.bootenv_unkept_done else R.string.bootenv_kept_done)) { c.admin.keepBootEnv(e.id, !e.keep) }
                        }
                    }
                    if (canDestroy && !e.active && !e.activated) {
                        SheetAction(stringResource(R.string.bootenv_delete), stringResource(R.string.bootenv_delete_sub), color = MaterialTheme.colorScheme.error) {
                            menu = null
                            destroy = e
                        }
                    }
                }
            }
        }

        activate?.let { e ->
            ConfirmDialog(
                title = stringResource(R.string.bootenv_activate_title, e.id),
                body = stringResource(R.string.bootenv_activate_body),
                confirmLabel = stringResource(R.string.bootenv_activate),
                destructive = false,
                onConfirm = {
                    activate = null
                    change(context.getString(R.string.bootenv_activated_done)) { c.admin.activateBootEnv(e.id) }
                },
                onDismiss = { activate = null },
            )
        }
        destroy?.let { e ->
            ConfirmDialog(
                title = stringResource(R.string.bootenv_delete_title, e.id),
                body = stringResource(R.string.bootenv_delete_body, formatBytes(e.usedBytes)),
                confirmLabel = stringResource(R.string.bootenv_delete),
                destructive = true,
                typeToConfirm = e.id,
                onConfirm = {
                    destroy = null
                    change(context.getString(R.string.bootenv_deleted_done)) { c.admin.destroyBootEnv(e.id) }
                },
                onDismiss = { destroy = null },
            )
        }
    }
}
