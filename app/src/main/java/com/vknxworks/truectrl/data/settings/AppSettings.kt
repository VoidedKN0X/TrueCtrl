package com.vknxworks.truectrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.truectrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide preferences (not per server). Server settings and secrets live elsewhere, encrypted. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "truectrl")

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Pool usage (percent) from which a pool is flagged in the attention list. */
    val poolWarnPercent: Int = 80,
    /** A replication, cloud sync or rsync task whose last success is older than this is flagged. */
    val backupMaxAgeDays: Int = 7,
    /** Ask for the fingerprint, face or screen lock when TrueCtrl opens. */
    val appLock: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val poolWarn = intPreferencesKey("pool_warn_percent")
        val backupAge = intPreferencesKey("backup_max_age_days")
        val appLock = booleanPreferencesKey("app_lock")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The stored settings as they're read and changed, without [settings]' placeholder defaults at start-up. */
    val stored: Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    init {
        // Versions before 0.9.2 stored the choice for the old opt-in stats under keys starting with
        // "usage_". Nothing uses them any more, so they're deleted from the phone.
        scope.launch {
            runCatching {
                context.appDataStore.edit { p ->
                    p.asMap().keys.filter { it.name.startsWith("usage_") }.forEach { p.remove(it) }
                }
            }
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        poolWarnPercent = (this[K.poolWarn] ?: 80).coerceIn(50, 99),
        backupMaxAgeDays = (this[K.backupAge] ?: 7).coerceIn(1, 90),
        appLock = this[K.appLock] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.poolWarn] = s.poolWarnPercent
            p[K.backupAge] = s.backupMaxAgeDays
            p[K.appLock] = s.appLock
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
