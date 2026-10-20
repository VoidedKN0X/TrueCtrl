package com.vknxworks.truectrl

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.vknxworks.truectrl.ui.lock.LockScreen
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.data.settings.ThemeChoice
import com.vknxworks.truectrl.ui.nav.AppRoot
import com.vknxworks.truectrl.ui.theme.TrueCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The recent-apps view shows a blank card instead of a snapshot of pools, datasets and shares.
        setRecentsScreenshotEnabled(false)
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val locked by c.appLock.locked.collectAsStateWithLifecycle()
            TrueCtrlTheme(settings.theme, settings.dynamicColor) {
                Box(Modifier.fillMaxSize()) {
                    AppRoot()
                    if (locked && settings.appLock) {
                        // Back leaves the app instead of moving around underneath the lock.
                        BackHandler { moveTaskToBack(true) }
                        LockScreen()
                    }
                }
            }
        }
    }
}
