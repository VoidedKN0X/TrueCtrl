package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.IscsiExtent
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.theme.Health

/** iSCSI at a glance: the service, each target with what it shares and to whom, the extents and who is connected. */
@Composable
fun IscsiScreen() {
    val c = LocalContext.current.container
    LoadScreen(
        title = stringResource(R.string.iscsi_title),
        icon = Icons.Outlined.SettingsEthernet,
        failedTitle = stringResource(R.string.iscsi_failed),
        load = { c.admin.iscsi() },
        pollMs = 30_000L,
    ) { iscsi ->
        val running = iscsi.service?.running == true
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.iscsi_service)) {
                GroupRow(
                    stringResource(if (running) R.string.iscsi_running else R.string.iscsi_stopped),
                    listOfNotNull(
                        stringResource(if (iscsi.service?.enable == true) R.string.iscsi_on_boot else R.string.iscsi_not_on_boot),
                        iscsi.listenPort?.let { stringResource(R.string.iscsi_port, it) },
                    ).joinToString("  ·  "),
                    Icons.Outlined.SettingsEthernet,
                    iconTint = if (running) Health.ok else Health.idle,
                )
                if (iscsi.basename.isNotBlank()) GroupRow(stringResource(R.string.iscsi_basename), iscsi.basename)
            }

            Group(stringResource(R.string.iscsi_targets)) {
                if (iscsi.targets.isEmpty()) GroupRow(stringResource(R.string.iscsi_no_targets))
                iscsi.targets.forEach { t ->
                    val lines = buildList {
                        t.alias?.let { add(it) }
                        if (t.mode.isNotBlank() && t.mode != "ISCSI") add(stringResource(R.string.iscsi_mode, t.mode))
                        if (t.luns.isEmpty()) add(stringResource(R.string.iscsi_no_luns))
                        t.luns.forEach { l -> add(stringResource(R.string.iscsi_lun, l.lunId, l.extent?.let { extentLine(it) } ?: stringResource(R.string.no_value))) }
                        t.groups.forEach { g ->
                            add(
                                listOfNotNull(
                                    stringResource(R.string.iscsi_portal, g.portal.ifBlank { stringResource(R.string.no_value) }),
                                    stringResource(R.string.iscsi_initiators, g.initiators?.ifBlank { null } ?: stringResource(R.string.iscsi_anyone)),
                                    if (g.authMethod.isNotBlank() && g.authMethod != "NONE") g.authMethod.replace('_', ' ') else null,
                                ).joinToString("  ·  "),
                            )
                        }
                        if (t.networks.isNotEmpty()) add(stringResource(R.string.iscsi_networks, t.networks.joinToString()))
                    }
                    GroupRow(if (iscsi.basename.isNotBlank()) iscsi.basename + ":" + t.name else t.name, lines.joinToString("\n"))
                }
            }

            Group(stringResource(R.string.iscsi_extents)) {
                if (iscsi.extents.isEmpty()) GroupRow(stringResource(R.string.iscsi_no_extents))
                iscsi.extents.forEach { e ->
                    GroupRow(
                        e.name,
                        listOfNotNull(
                            e.source.ifBlank { null },
                            stringResource(if (e.type == "FILE") R.string.iscsi_extent_file else R.string.iscsi_extent_disk),
                            if (!e.enabled) stringResource(R.string.iscsi_extent_off) else null,
                            if (e.readOnly) stringResource(R.string.iscsi_extent_ro) else null,
                            if (e.locked) stringResource(R.string.iscsi_extent_locked) else null,
                            e.comment.ifBlank { null },
                        ).joinToString("  ·  "),
                    )
                }
            }

            iscsi.sessions?.let { sessions ->
                Group(stringResource(R.string.iscsi_sessions)) {
                    if (sessions.isEmpty()) GroupRow(stringResource(R.string.iscsi_no_sessions))
                    sessions.forEach { s ->
                        GroupRow(s.alias ?: s.initiator, listOfNotNull(s.address.ifBlank { null }, if (s.alias != null) s.initiator else null, s.target.ifBlank { null }).joinToString("\n"))
                    }
                }
            }
        }
    }
}

private fun extentLine(e: IscsiExtent): String = listOf(e.name, e.source).filter { it.isNotBlank() }.joinToString("  ·  ")
