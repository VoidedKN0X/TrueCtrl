package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.formatUptime
import com.vknxworks.truectrl.ui.overview.SeverityIcon
import com.vknxworks.truectrl.ui.overview.alertText
import com.vknxworks.truectrl.ui.storage.formatTemp
import com.vknxworks.truectrl.ui.theme.Health
import java.util.Locale

/** The UPS: whether the service runs, the battery and power readings, TrueNAS's UPS alerts and how it's set up. */
@Composable
fun UpsScreen() {
    val c = LocalContext.current.container
    val overview by c.overview.state.collectAsStateWithLifecycle()
    // TrueNAS reports "on battery", "battery low" and "lost contact" as alerts of the UPS classes.
    val alerts = overview.snapshot.alerts.filter { !it.dismissed && it.klass.startsWith("UPS", ignoreCase = true) }

    LoadScreen(
        title = stringResource(R.string.ups_title),
        icon = Icons.Outlined.BatteryChargingFull,
        failedTitle = stringResource(R.string.ups_failed),
        load = { c.admin.ups() },
        pollMs = 30_000L,
    ) { ups ->
        val running = ups.service?.running == true
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.ups_status)) {
                GroupRow(
                    stringResource(if (running) R.string.ups_service_running else R.string.ups_service_stopped),
                    stringResource(
                        when {
                            running -> R.string.ups_service_running_sub
                            ups.service?.enable == true -> R.string.ups_service_stopped_enabled_sub
                            else -> R.string.ups_service_stopped_sub
                        },
                    ),
                    Icons.Outlined.BatteryChargingFull,
                    iconTint = if (running) Health.ok else Health.idle,
                )
                alerts.forEach { a ->
                    GroupRow(alertText(a.text), trailing = { SeverityIcon(a.severity) })
                }
            }
            if (running) {
                Group(stringResource(R.string.ups_readings), footer = if (ups.hasReadings) stringResource(R.string.ups_readings_footer) else null) {
                    if (!ups.hasReadings) GroupRow(stringResource(R.string.ups_no_readings), stringResource(R.string.ups_no_readings_sub))
                    ups.charge?.let { GroupRow(stringResource(R.string.ups_charge), stringResource(R.string.percent, it.toInt())) }
                    ups.runtimeSeconds?.let { GroupRow(stringResource(R.string.ups_runtime), formatUptime(it.toLong())) }
                    ups.load?.let { GroupRow(stringResource(R.string.ups_load), stringResource(R.string.percent, it.toInt())) }
                    ups.inputVoltage?.let { GroupRow(stringResource(R.string.ups_input_voltage), volts(it)) }
                    ups.outputVoltage?.let { GroupRow(stringResource(R.string.ups_output_voltage), volts(it)) }
                    ups.batteryVoltage?.let { GroupRow(stringResource(R.string.ups_battery_voltage), volts(it)) }
                    ups.temperature?.let { GroupRow(stringResource(R.string.ups_temperature), formatTemp(it)) }
                }
            }
            Group(stringResource(R.string.ups_setup)) {
                GroupRow(stringResource(R.string.ups_name), listOf(ups.identifier, ups.description).filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { stringResource(R.string.no_value) })
                GroupRow(
                    stringResource(R.string.ups_mode),
                    stringResource(if (ups.mode == "SLAVE") R.string.ups_mode_slave else R.string.ups_mode_master) +
                        if (ups.mode == "SLAVE" && ups.remoteHost.isNotBlank()) "  ·  " + ups.remoteHost else "",
                )
                if (ups.mode != "SLAVE") {
                    GroupRow(stringResource(R.string.ups_driver), listOf(ups.driver.substringBefore('$'), ups.port).filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { stringResource(R.string.no_value) })
                }
                GroupRow(
                    stringResource(R.string.ups_shutdown),
                    stringResource(if (ups.shutdown == "LOWBATT") R.string.ups_shutdown_low else R.string.ups_shutdown_battery) +
                        (ups.shutdownTimer?.let { "  ·  " + stringResource(R.string.ups_shutdown_timer, it) } ?: ""),
                )
            }
        }
    }
}

private fun volts(v: Double): String = String.format(Locale.getDefault(), "%.1f V", v)
