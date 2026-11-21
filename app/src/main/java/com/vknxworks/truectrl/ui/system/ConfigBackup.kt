package com.vknxworks.truectrl.ui.system

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.components.CtrlDialog
import com.vknxworks.truectrl.ui.components.CtrlProgressBar
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.components.formatBytes
import com.vknxworks.truectrl.ui.lock.AppLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves TrueNAS's configuration backup to a file you pick. Asks what to include first: the
 * secret seed (needed to read the saved passwords back after a fresh install) and root's SSH
 * keys both make the file more sensitive, so both start off. The download runs in the app's own
 * scope, so it finishes even when this screen is left; [onDone] gets the message to show.
 */
@Composable
fun ConfigBackupDialog(hostname: String, version: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    var seed by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val host = c.sessions.active.value?.server?.host.orEmpty()
        val name = fileName(hostname, version, seed || keys)
        running = true
        c.appScope.launch {
            val message = try {
                val bytes = c.admin.downloadConfig(seed, keys, name) {
                    context.contentResolver.openOutputStream(uri) ?: throw IOException("Could not open the file")
                }
                if (bytes <= 0) throw IOException("Empty download")
                context.getString(R.string.config_saved, formatBytes(bytes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The file was made a moment ago for this download and holds nothing useful: remove it again.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                context.getString(R.string.config_failed, errorMessage(context, e, host))
            }
            running = false
            onDone(message)
        }
    }

    CtrlDialog(
        onDismissRequest = { if (!running) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !running, dismissOnClickOutside = !running),
        title = { Text(stringResource(R.string.config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    Text(stringResource(R.string.config_downloading))
                    CtrlProgressBar(progress = null, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(stringResource(R.string.config_body))
                    GroupSwitch(stringResource(R.string.config_seed), stringResource(R.string.config_seed_sub), checked = seed) { seed = it }
                    GroupSwitch(stringResource(R.string.config_keys), stringResource(R.string.config_keys_sub), checked = keys) { keys = it }
                    Text(stringResource(R.string.config_warning), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !running,
                onClick = {
                    // Android's file picker opens over TrueCtrl: that isn't leaving the app.
                    AppLock.expectReturn()
                    runCatching { launcher.launch(fileName(hostname, version, seed || keys)) }
                },
            ) { Text(stringResource(R.string.config_save)) }
        },
        dismissButton = { TextButton(enabled = !running, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "truenas-25.10.1-20261121-184500.tar": a tar when extras are included, otherwise the database file. */
private fun fileName(hostname: String, version: String, tar: Boolean): String {
    val safe = { s: String -> s.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-') }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return listOf(safe(hostname).ifBlank { "truenas" }, safe(version), stamp).filter { it.isNotBlank() }.joinToString("-") + if (tar) ".tar" else ".db"
}
