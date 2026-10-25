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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val stored by c.settings.stored.collectAsState(initial = null)
            // Until the settings are read, trust the synchronous read made at start-up.
            val lockSetting = stored?.appLock ?: c.lockAtStart
            val covered = lockCovering(lockSetting, c.vault)
            // "Hide in screenshots" and "Hide in recent apps" (both off by default). FLAG_SECURE is always on
            // for the lock and PIN screens and the screens where secrets are typed (see SecureWindow), and
            // with the app lock on Recents always shows a blank card (a preview would skip the PIN).
            val secure = settings.hideScreenshots || covered || ScreenPrivacy.forced > 0
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || lockSetting || c.vault.enabled)
            TrueCtrlTheme {
                Box(Modifier.fillMaxSize()) {
                    // The app stays underneath (so nothing you typed is lost), hidden from TalkBack too.
                    Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { AppRoot() }
                    AppLockOverlay(lockSetting)
                }
            }
        }
    }
}
