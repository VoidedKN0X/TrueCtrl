package com.vknxworks.truectrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R

/**
 * Asks before something disruptive or destructive. With [typeToConfirm], the button only works
 * once that exact text (a dataset path or hostname) is typed, for the most dangerous actions.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    typeToConfirm: String? = null,
) {
    var typed by remember { mutableStateOf("") }
    val ok = typeToConfirm == null || typed == typeToConfirm
    CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                if (typeToConfirm != null) {
                    Text(stringResource(R.string.confirm_type, typeToConfirm), style = MaterialTheme.typography.bodyMedium)
                    CtrlTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ok) {
                Text(confirmLabel, color = if (destructive && ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.takeIf { ok } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
