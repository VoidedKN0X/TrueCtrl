package com.vknxworks.truectrl.ui.components

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Screens that always get FLAG_SECURE, whatever "Hide in screenshots" says: where an
 * API key, password, one-time password or dataset passphrase is typed, and the shell. MainActivity
 * applies the flag. A count rather than a flag, so two such screens overlapping during a transition
 * can't clear it early, and leaving one never lifts the user's own setting.
 */
object ScreenPrivacy {
    var forced by mutableIntStateOf(0)
        private set

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }

    /**
     * Screens that hide other apps' floating windows while they show (PIN entry and change, passwords,
     * keys, "can't be undone" confirmations). Counted, so nested screens can't clear it early.
     */
    var shielded by mutableIntStateOf(0)
        private set

    /** Views marked as sensitive for accessibility services, with how many screens asked for it. */
    private val sensitiveViews = java.util.WeakHashMap<android.view.View, Int>()

    internal fun shield() { shielded++ }
    internal fun unshield() { shielded = (shielded - 1).coerceAtLeast(0) }

    /**
     * Only accessibility services that are real accessibility tools (screen readers, switch access) can
     * read [view] and everything in it while it's marked.
     */
    internal fun markSensitive(view: android.view.View) {
        val n = sensitiveViews[view] ?: 0
        sensitiveViews[view] = n + 1
        if (n == 0) runCatching { view.setAccessibilityDataSensitive(android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES) }
    }

    internal fun unmarkSensitive(view: android.view.View) {
        val n = sensitiveViews[view] ?: return
        if (n > 1) {
            sensitiveViews[view] = n - 1
        } else {
            sensitiveViews.remove(view)
            runCatching { view.setAccessibilityDataSensitive(android.view.View.ACCESSIBILITY_DATA_SENSITIVE_AUTO) }
        }
    }
}

/**
 * True while the app lock (or a PIN screen) covers the app. Provided by MainActivity around the app
 * only, not around the lock screen. Dialogs, bottom sheets and the shell open their own windows or
 * views that would be drawn above the lock screen, so they read this and stay hidden while it's true.
 */
val LocalUnderLock = compositionLocalOf { false }

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
 * Applies "Hide in screenshots" ([secure]: FLAG_SECURE, which also blanks the recent-apps preview) and
 * "Hide in recent apps" ([hideRecents]: a blank card in Recents, screenshots still work) to [activity]'s
 * window, and again whenever they change, so a switch works at once in every open screen.
 */
@Composable
fun WindowPrivacy(activity: Activity, secure: Boolean, hideRecents: Boolean) {
    LaunchedEffect(secure) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    LaunchedEffect(hideRecents) { activity.setRecentsScreenshotEnabled(!hideRecents) }
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

/**
 * While the calling screen is shown: other apps' floating windows are hidden (so none can sit over a PIN
 * pad, a key field or a confirm button), and with [sensitive] the window it's in is hidden from
 * accessibility services that aren't real accessibility tools. Call it inside a dialog's content to
 * cover that dialog's window.
 */
@Composable
fun ShieldScreen(sensitive: Boolean = true) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, sensitive) {
        ScreenPrivacy.shield()
        if (sensitive) ScreenPrivacy.markSensitive(view)
        // Inside a dialog: its own window hides floating windows too, not only the activity's behind it.
        val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        dialogWindow?.let { runCatching { it.setHideOverlayWindows(true) } }
        onDispose {
            ScreenPrivacy.unshield()
            if (sensitive) ScreenPrivacy.unmarkSensitive(view)
            dialogWindow?.let { runCatching { it.setHideOverlayWindows(false) } }
        }
    }
}

/**
 * Hides other apps' floating windows (chat heads, overlays) over [activity] while [hide] is true; the rest
 * of the time they work as usual. Needs HIDE_OVERLAY_WINDOWS, which Android grants at install.
 */
@Composable
fun OverlayShield(activity: android.app.Activity, hide: Boolean) {
    DisposableEffect(hide) {
        runCatching { activity.window.setHideOverlayWindows(hide) }
        onDispose { }
    }
}
