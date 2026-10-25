package com.vknxworks.truectrl.ui.components

import androidx.compose.material3.ButtonDefaults
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.ui.theme.Gutter

/**
 * The "stale" marker: the last refresh failed, so what's on screen is the last known state from
 * [updatedAt]. Says why and offers a retry.
 */
@Composable
fun StaleBanner(updatedAt: Long, error: Throwable, host: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    val reason = errorMessage(context, error, host)
    val text = if (updatedAt > 0) {
        stringResource(R.string.stale_banner, relativeTime(updatedAt), reason)
    } else {
        reason
    }
    Surface(
        shape = CtrlShapes.Card,
        color = CtrlColors.Surface,
        border = CtrlColors.CardStroke,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.History, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = CtrlColors.AccentText)) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
