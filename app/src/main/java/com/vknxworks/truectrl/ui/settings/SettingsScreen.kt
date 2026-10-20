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
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupChoice
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

/** App settings: appearance, notifications, warnings and app lock. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val themeOptions = ThemeChoice.entries.map { it to stringResource(it.label) }

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
                GroupRow(
                    stringResource(R.string.notifications),
                    stringResource(R.string.notifications_subtitle),
                    icon = Icons.Filled.Notifications,
                    showChevron = true,
                    onClick = { nav.navigate(NotificationsRoute) },
                )
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
            Group(stringResource(R.string.group_security), footer = stringResource(R.string.app_lock_footer)) {
                GroupSwitch(
                    stringResource(R.string.app_lock),
                    stringResource(R.string.app_lock_subtitle),
                    checked = settings.appLock,
                    icon = Icons.Filled.Fingerprint,
                ) { on ->
                    if (on && !com.vknxworks.truectrl.data.lock.AppLock.available(context)) {
                        android.widget.Toast.makeText(context, context.getString(R.string.app_lock_unavailable), android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        if (on) c.appLock.unlocked()
                        c.appScope.launch { c.settings.update { it.copy(appLock = on) } }
                    }
                }
            }
        }
    }
}
