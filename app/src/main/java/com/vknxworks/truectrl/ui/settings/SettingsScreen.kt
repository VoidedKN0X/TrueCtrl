package com.vknxworks.truectrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.Notifications
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.NotificationsRoute
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.settings.ThemeChoice
import com.vknxworks.truectrl.data.settings.LockTimeout
import com.vknxworks.truectrl.data.lock.LockVault
import com.vknxworks.truectrl.ui.lock.AppLock
import com.vknxworks.truectrl.ui.lock.PinRequest
import com.vknxworks.truectrl.ui.lock.findActivity
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Layers
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupChoice
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.rememberNotificationAccess
import kotlinx.coroutines.launch

/** App settings: appearance, notifications, warnings and app lock. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val themeOptions = ThemeChoice.entries.map { it to stringResource(it.label) }
    val bioAvailable = remember { AppLock.biometricAvailable(context) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.group_appearance)) {
                GroupChoice(
                    stringResource(R.string.theme),
                    themeOptions,
                    settings.theme,
                    icon = Icons.Filled.DarkMode,
                ) { choice -> c.appScope.launch { c.settings.update { it.copy(theme = choice) } } }
                GroupSwitch(
                    stringResource(R.string.dynamic_colour),
                    stringResource(R.string.dynamic_colour_subtitle),
                    checked = settings.dynamicColor,
                    icon = Icons.Filled.Palette,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(dynamicColor = on) } } }
            }
            Group(stringResource(R.string.group_notifications)) {
                val notify by c.notifySettings.settings.collectAsStateWithLifecycle()
                val notificationAccess = rememberNotificationAccess()
                GroupRow(
                    stringResource(R.string.notifications),
                    stringResource(
                        if ((notify.polling || notify.push) && !notificationAccess.canPost) R.string.notifications_subtitle_blocked else R.string.notifications_subtitle,
                    ),
                    icon = Icons.Filled.Notifications,
                    showChevron = true,
                    onClick = { nav.navigate(NotificationsRoute) },
                )
            }
            val notify by c.notifySettings.settings.collectAsStateWithLifecycle()
            val lockOn = c.vault.enabled
            val backgroundChecks = notify.polling || notify.push
            Group(
                stringResource(R.string.group_security),
                footer = stringResource(if (lockOn && backgroundChecks) R.string.app_lock_footer_background else R.string.app_lock_footer),
            ) {
                GroupSwitch(
                    stringResource(R.string.app_lock),
                    stringResource(R.string.app_lock_subtitle),
                    checked = lockOn,
                    icon = Icons.Filled.Lock,
                ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Filled.Password,
                        showChevron = true,
                        onClick = { AppLock.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                        checked = c.vault.biometricEnabled,
                        icon = Icons.Filled.Fingerprint,
                    ) { on ->
                        if (!on) {
                            c.vault.disableBiometric()
                        } else if (bioAvailable) {
                            val activity = context.findActivity()
                            val cipher = c.vault.biometricEnrolCipher()
                            if (activity == null || cipher == null) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            } else {
                                AppLock.biometricPrompt(
                                    activity,
                                    context.getString(R.string.lock_fingerprint_enable_title),
                                    context.getString(R.string.lock_fingerprint_enable_subtitle),
                                    cipher,
                                ) { unlocked ->
                                    if (!c.vault.finishBiometricEnrol(unlocked)) {
                                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        LockTimeout.entries.map { it to stringResource(it.label) },
                        settings.lockTimeout,
                        icon = Icons.Filled.Timer,
                    ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    checked = settings.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    checked = settings.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
            }
            Group(stringResource(R.string.group_warnings)) {
                GroupChoice(
                    stringResource(R.string.pool_warn_title),
                    listOf(70, 75, 80, 85, 90, 95).map { it to stringResource(R.string.pool_warn_subtitle, it) },
                    settings.poolWarnPercent,
                    icon = Icons.Filled.Storage,
                ) { pct -> c.appScope.launch { c.settings.update { it.copy(poolWarnPercent = pct) } } }
                GroupChoice(
                    stringResource(R.string.backup_age_title),
                    listOf(1, 2, 3, 7, 14, 30).map { it to pluralStringResource(R.plurals.backup_age_value, it, it) },
                    settings.backupMaxAgeDays,
                    icon = Icons.Filled.History,
                ) { d -> c.appScope.launch { c.settings.update { it.copy(backupMaxAgeDays = d) } } }
            }
        }
    }
}
