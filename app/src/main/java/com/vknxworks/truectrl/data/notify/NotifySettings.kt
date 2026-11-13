package com.vknxworks.truectrl.data.notify

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Things TrueCtrl can notify about. Each can be switched off. */
enum class NotifyEvent(@StringRes val label: Int, @StringRes val summary: Int) {
    NEW_ALERT(R.string.ev_alert, R.string.ev_alert_sub),
    POOL_UNHEALTHY(R.string.ev_pool, R.string.ev_pool_sub),
    POOL_FULL(R.string.ev_pool_full, R.string.ev_pool_full_sub),
    SMART(R.string.ev_smart, R.string.ev_smart_sub),
    SCRUB_ERRORS(R.string.ev_scrub, R.string.ev_scrub_sub),
    TASK_FAILED(R.string.ev_task, R.string.ev_task_sub),
    APP_CRASHED(R.string.ev_app, R.string.ev_app_sub),
    UPDATES(R.string.ev_update, R.string.ev_update_sub),
    UNREACHABLE(R.string.ev_unreachable, R.string.ev_unreachable_sub),
}

data class NotifySettings(
    /** Check the servers in the background with WorkManager. */
    val polling: Boolean = false,
    val intervalMinutes: Int = 15,
    /** Receive TrueNAS's own alerts through the ntfy app. */
    val push: Boolean = false,
    val disabled: Set<NotifyEvent> = emptySet(),
    /** The least serious TrueNAS alert level that's notified. */
    val minAlertLevel: Severity = Severity.WARNING,
    /** The separate disk-health check (pools, disks, SMART, disk alerts). Off until turned on. */
    val diskHealth: Boolean = false,
    val diskHealthMinutes: Int = 60,
    /** Also post new disk-health warnings to a topic on the user's ntfy server. Off until turned on. */
    val ntfyPost: Boolean = false,
    val ntfyServer: String = "",
    val ntfyTopic: String = "",
    /** SHA-256 of the ntfy server's self-signed certificate, once the user trusted it. */
    val ntfyCertPin: String = "",
) {
    fun on(e: NotifyEvent): Boolean = e !in disabled

    /** Background work signs in to TrueNAS while the app is locked. */
    val needsBackgroundSignIn: Boolean get() = polling || push || diskHealth

    companion object {
        val INTERVALS = listOf(15, 30, 60, 120)

        /** Disk-health check intervals in minutes: from 15 minutes (Android's minimum) to once a day. */
        val DISK_INTERVALS = listOf(15, 30, 60, 120, 360, 720, 1440)
    }
}

class NotifySettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val polling = booleanPreferencesKey("notify_polling")
        val interval = intPreferencesKey("notify_interval")
        val push = booleanPreferencesKey("notify_push")
        val disabled = stringSetPreferencesKey("notify_disabled")
        val minLevel = stringPreferencesKey("notify_min_level")
        val diskHealth = booleanPreferencesKey("disk_health")
        val diskMinutes = intPreferencesKey("disk_health_minutes")
        val ntfyPost = booleanPreferencesKey("disk_ntfy_post")
        val ntfyServer = stringPreferencesKey("disk_ntfy_server")
        val ntfyTopic = stringPreferencesKey("disk_ntfy_topic")
        val ntfyPin = stringPreferencesKey("disk_ntfy_cert_pin")
    }

    val settings: StateFlow<NotifySettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, NotifySettings())

    /** The saved settings, read directly (for the background worker, before the flow has a value). */
    suspend fun current(): NotifySettings = context.appDataStore.data.first().toSettings()

    private fun Preferences.toSettings() = NotifySettings(
        polling = this[K.polling] ?: false,
        intervalMinutes = (this[K.interval] ?: 15).let { if (it in NotifySettings.INTERVALS) it else 15 },
        push = this[K.push] ?: false,
        disabled = this[K.disabled].orEmpty().mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }.toSet(),
        minAlertLevel = this[K.minLevel]?.let { n -> Severity.entries.firstOrNull { it.name == n } } ?: Severity.WARNING,
        diskHealth = this[K.diskHealth] ?: false,
        diskHealthMinutes = (this[K.diskMinutes] ?: 60).let { if (it in NotifySettings.DISK_INTERVALS) it else 60 },
        ntfyPost = this[K.ntfyPost] ?: false,
        ntfyServer = this[K.ntfyServer].orEmpty(),
        ntfyTopic = this[K.ntfyTopic].orEmpty(),
        ntfyCertPin = this[K.ntfyPin].orEmpty(),
    )

    suspend fun update(transform: (NotifySettings) -> NotifySettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.polling] = s.polling
            p[K.interval] = s.intervalMinutes
            p[K.push] = s.push
            p[K.disabled] = s.disabled.map { it.name }.toSet()
            p[K.minLevel] = s.minAlertLevel.name
            p[K.diskHealth] = s.diskHealth
            p[K.diskMinutes] = s.diskHealthMinutes
            p[K.ntfyPost] = s.ntfyPost
            p[K.ntfyServer] = s.ntfyServer
            p[K.ntfyTopic] = s.ntfyTopic
            p[K.ntfyPin] = s.ntfyCertPin
        }
    }
}
