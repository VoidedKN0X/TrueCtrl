package com.vknxworks.truectrl.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Screens that always get FLAG_SECURE, whatever "Hide in screenshots and recent apps" says: where an
 * API key, password, one-time password or dataset passphrase is typed, and the shell. MainActivity
 * applies the flag. A count rather than a flag, so two such screens overlapping during a transition
 * can't clear it early, and leaving one never lifts the user's own setting.
 */
object ScreenPrivacy {
    var forced by mutableIntStateOf(0)
        private set

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }
}

/**
 * Keeps the window out of screenshots, screen recordings, casting and the recent-apps preview
 * (FLAG_SECURE) while the calling screen is shown. Dialogs opened from such a screen inherit it.
 */
@Composable
fun SecureWindow() {
    DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}

/**
 * Copies [text] marked as sensitive, so Android hides it from the clipboard preview and keyboards
 * don't keep it in their clipboard history.
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clip = android.content.ClipData.newPlainText(label, text).apply {
        description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
}
