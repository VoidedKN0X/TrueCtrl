package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.GuestVerb
import com.vknxworks.truectrl.data.system.InstanceInfo
import com.vknxworks.truectrl.data.system.VmInfo
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.CtrlSheet
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.theme.Health

/** A VM (by number) or a container (by name) picked on the screen. */
private data class Guest(val vmId: Int?, val instanceId: String?, val name: String, val running: Boolean)

/** Virtual machines and containers: what runs, and start, shut down, restart and power off. */
@Composable
fun GuestsScreen() {
    val context = LocalContext.current
    val c = context.container
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val readOnly = active?.server?.readOnly == true
    val caps = access.capabilities
    val canVm = !readOnly && caps?.has("vm.start") == true && caps.hasRole("VM_WRITE")
    val canInstance = !readOnly && caps?.has("virt.instance.start") == true && caps.hasRole("VIRT_INSTANCE_WRITE")
    var menu by remember { mutableStateOf<Guest?>(null) }
    var confirm by remember { mutableStateOf<Pair<Guest, GuestVerb>?>(null) }

    LoadScreen(
        title = stringResource(R.string.guests_title),
        icon = Icons.Outlined.Computer,
        failedTitle = stringResource(R.string.guests_failed),
        load = { c.admin.guests() },
        pollMs = 10_000L,
    ) { guests ->
        fun run(g: Guest, verb: GuestVerb) {
            val done = context.getString(
                when (verb) {
                    GuestVerb.START -> R.string.guest_started
                    GuestVerb.STOP -> R.string.guest_stopping
                    GuestVerb.POWER_OFF -> R.string.guest_powered_off
                    GuestVerb.RESTART -> R.string.guest_restarting
                },
                g.name,
            )
            change(done) {
                if (g.vmId != null) c.admin.vm(g.vmId, verb) else if (g.instanceId != null) c.admin.instance(g.instanceId, verb)
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            guests.vms?.let { vms ->
                Group(
                    stringResource(R.string.guests_vms),
                    footer = when {
                        readOnly -> stringResource(R.string.read_only_hint)
                        !canVm && vms.isNotEmpty() -> stringResource(R.string.guests_not_allowed)
                        else -> null
                    },
                ) {
                    if (vms.isEmpty()) GroupRow(stringResource(R.string.guests_no_vms))
                    vms.forEach { vm ->
                        GroupRow(
                            vm.name,
                            vmLine(vm),
                            Icons.Outlined.Computer,
                            iconTint = if (vm.running) Health.ok else Health.idle,
                            showChevron = canVm,
                            onClick = if (canVm && !busy) ({ menu = Guest(vm.id, null, vm.name, vm.running) }) else null,
                        )
                    }
                }
            }
            guests.instances?.let { list ->
                Group(
                    stringResource(R.string.guests_containers),
                    footer = when {
                        readOnly && guests.vms == null -> stringResource(R.string.read_only_hint)
                        !readOnly && !canInstance && list.isNotEmpty() -> stringResource(R.string.guests_not_allowed)
                        else -> null
                    },
                ) {
                    if (list.isEmpty()) GroupRow(stringResource(R.string.guests_no_containers))
                    list.forEach { i ->
                        GroupRow(
                            i.name,
                            instanceLine(i),
                            Icons.Outlined.Inventory2,
                            iconTint = when {
                                i.running -> Health.ok
                                i.status == "ERROR" -> Health.critical
                                else -> Health.idle
                            },
                            showChevron = canInstance,
                            onClick = if (canInstance && !busy) ({ menu = Guest(null, i.id, i.name, i.running) }) else null,
                        )
                    }
                }
            }
        }

        menu?.let { g ->
            CtrlSheet(onDismissRequest = { menu = null }) {
                SheetTitle(g.name)
                Column(Modifier.navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp)) {
                    if (!g.running) {
                        SheetAction(stringResource(R.string.guest_start)) {
                            menu = null
                            run(g, GuestVerb.START)
                        }
                    } else {
                        SheetAction(stringResource(R.string.guest_stop), stringResource(R.string.guest_stop_sub)) {
                            menu = null
                            confirm = g to GuestVerb.STOP
                        }
                        SheetAction(stringResource(R.string.guest_restart), stringResource(R.string.guest_restart_sub)) {
                            menu = null
                            confirm = g to GuestVerb.RESTART
                        }
                        SheetAction(stringResource(R.string.guest_power_off), stringResource(R.string.guest_power_off_sub), color = MaterialTheme.colorScheme.error) {
                            menu = null
                            confirm = g to GuestVerb.POWER_OFF
                        }
                    }
                }
            }
        }

        confirm?.let { (g, verb) ->
            ConfirmDialog(
                title = stringResource(
                    when (verb) {
                        GuestVerb.RESTART -> R.string.guest_restart_title
                        GuestVerb.POWER_OFF -> R.string.guest_power_off_title
                        else -> R.string.guest_stop_title
                    },
                    g.name,
                ),
                body = stringResource(
                    when (verb) {
                        GuestVerb.RESTART -> R.string.guest_restart_body
                        GuestVerb.POWER_OFF -> R.string.guest_power_off_body
                        else -> R.string.guest_stop_body
                    },
                ),
                confirmLabel = stringResource(
                    when (verb) {
                        GuestVerb.RESTART -> R.string.guest_restart
                        GuestVerb.POWER_OFF -> R.string.guest_power_off
                        else -> R.string.guest_stop
                    },
                ),
                destructive = verb == GuestVerb.POWER_OFF,
                onConfirm = {
                    confirm = null
                    run(g, verb)
                },
                onDismiss = { confirm = null },
            )
        }
    }
}

@Composable
private fun guestState(state: String): String = when (state) {
    "RUNNING" -> stringResource(R.string.guest_state_running)
    "STOPPED" -> stringResource(R.string.guest_state_stopped)
    "SUSPENDED", "FROZEN" -> stringResource(R.string.guest_state_paused)
    "" -> stringResource(R.string.no_value)
    else -> state.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun vmLine(vm: VmInfo): String = listOfNotNull(
    guestState(vm.state),
    pluralStringResource(R.plurals.guest_cpus, vm.cpuCount, vm.cpuCount),
    if (vm.memoryMib > 0) formatBytes(vm.memoryMib * 1024 * 1024) else null,
    if (vm.autostart) stringResource(R.string.guest_autostart) else null,
    vm.description.ifBlank { null },
).joinToString("  ·  ")

@Composable
private fun instanceLine(i: InstanceInfo): String = listOfNotNull(
    guestState(i.status),
    if (i.type == "VM") stringResource(R.string.guest_type_vm) else null,
    i.image.ifBlank { null },
    i.memory?.let { formatBytes(it) },
    if (i.autostart) stringResource(R.string.guest_autostart) else null,
    i.addresses.firstOrNull(),
).joinToString("  ·  ")
