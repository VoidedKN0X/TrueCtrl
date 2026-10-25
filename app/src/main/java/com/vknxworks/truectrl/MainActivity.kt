package com.vknxworks.truectrl

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.vknxworks.truectrl.ui.lock.AppLockOverlay
import com.vknxworks.truectrl.ui.lock.lockCovering
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.ui.nav.AppRoot
import com.vknxworks.truectrl.ui.theme.TrueCtrlTheme
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.truectrl.ui.components.ScreenPrivacy
import com.vknxworks.truectrl.ui.components.WindowPrivacy

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bars follow the phone's dark / light setting; icons switch with it, the bar colour is the background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // uiMode changes don't recreate the activity (see the manifest), so update the bars here.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val stored by c.settings.stored.collectAsState(initial = null)
            // Until the settings are read, trust the synchronous read made at start-up.
            val lockSetting = stored?.appLock ?: c.lockAtStart
            val covered = lockCovering(lockSetting, c.vault)
            // "Hide in screenshots" and "Hide in recent apps" (both off by default). FLAG_SECURE is always on
            // for the lock and PIN screens and the screens where secrets are typed (see SecureWindow), and
            // with the app lock on Recents always shows a blank card (a preview would skip the PIN).
            val secure = settings.hideScreenshots || covered || ScreenPrivacy.forced > 0
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || lockSetting || c.vault.enabled)
            TrueCtrlTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    // The app stays underneath (so nothing you typed is lost), hidden from TalkBack too.
                    Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { AppRoot() }
                    AppLockOverlay(lockSetting)
                }
            }
        }
    }
}
