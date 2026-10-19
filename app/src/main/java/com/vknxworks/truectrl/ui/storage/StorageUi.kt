package com.vknxworks.truectrl.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.storage.DiskInfo
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.theme.Health
import java.util.Locale

/** ZFS states: ONLINE is fine, DEGRADED needs a look, anything else is critical. */
fun zfsColor(status: String): Color = when (status.uppercase()) {
    "ONLINE" -> Health.ok
    "DEGRADED" -> Health.warn
    "" -> Health.idle
    else -> Health.critical
}

/** Disk temperatures: warm from 50 °C, critical from 60 °C (TrueNAS's own default thresholds are similar). */
fun tempColor(c: Double?, normal: Color): Color = when {
    c == null -> normal
    c >= 60 -> Health.critical
    c >= 50 -> Health.warn
    else -> normal
}

fun formatTemp(c: Double): String = String.format(Locale.getDefault(), "%.0f °C", c)

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** "HDD, 7200 rpm, 4 TiB" style summary. */
@Composable
fun diskKind(d: DiskInfo): String = listOfNotNull(
    d.type.takeIf { it.isNotBlank() },
    d.rotationRate?.let { stringResource(R.string.disk_rpm, it) },
    d.size.takeIf { it > 0 }?.let(::formatBytes),
).joinToString(", ")

/** Shows a serial number shortened to its last characters until it's tapped. */
fun shortSerial(serial: String): String = if (serial.length <= 6) serial else "…" + serial.takeLast(4)

/** Vdev group names as TrueNAS shows them. */
@Composable
fun groupLabel(group: String): String = stringResource(
    when (group) {
        "data" -> R.string.vdev_data
        "log" -> R.string.vdev_log
        "cache" -> R.string.vdev_cache
        "spare" -> R.string.vdev_spare
        "special" -> R.string.vdev_special
        "dedup" -> R.string.vdev_dedup
        else -> R.string.vdev_data
    },
)
