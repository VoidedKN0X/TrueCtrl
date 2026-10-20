package com.vknxworks.truectrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.truectrl.data.notify.NotifySettingsRepository
import com.vknxworks.truectrl.data.overview.OverviewRepository
import com.vknxworks.truectrl.data.server.ServerStore
import com.vknxworks.truectrl.data.protection.ProtectionRepository
import com.vknxworks.truectrl.data.sharing.SharingRepository
import com.vknxworks.truectrl.data.system.SystemRepository
import com.vknxworks.truectrl.data.widget.HealthStore
import com.vknxworks.truectrl.data.storage.DatasetRepository
import com.vknxworks.truectrl.data.storage.StorageRepository
import com.vknxworks.truectrl.data.server.Sessions
import com.vknxworks.truectrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.vknxworks.truectrl.data.lock.AppLock
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. Created on the main thread in [TrueCtrlApp]. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)
    val notifySettings = NotifySettingsRepository(context, appScope)

    /**
     * Shared HTTP client. Each server gets a copy with its own certificate pin (see httpFor), so a
     * pinned certificate is only ever accepted for the server it belongs to. Redirects are not
     * followed, so a sign-in can never be sent to another host. The WebSocket is pinged every 30 s
     * so a dead connection (Wi-Fi gone, TrueNAS rebooting) is noticed and reconnected.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "TrueCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .build()

    /** Read once, synchronously, so the app never shows unlocked for a moment at start. */
    val appLock = AppLock(context, runBlocking { runCatching { settings.stored.first().appLock }.getOrDefault(false) })

    val serverStore = ServerStore(context)
    val sessions = Sessions(serverStore, http, appScope)
    val overview = OverviewRepository(context, sessions, appScope) { server, snapshot ->
        val s = settings.settings.value
        HealthStore.save(context, HealthStore.from(server, snapshot, s.poolWarnPercent, s.backupMaxAgeDays))
    }
    val storage = StorageRepository(context, sessions, appScope)
    val datasets = DatasetRepository(context, sessions, appScope)
    val protection = ProtectionRepository(context, sessions, appScope)
    val sharing = SharingRepository(context, sessions, appScope)
    val system = SystemRepository(sessions)

    init {
        // One live connection while the app is on screen; background checks run in NotifyWorker.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = sessions.setForeground(true)
            override fun onStop(owner: LifecycleOwner) {
                sessions.setForeground(false)
                if (settings.settings.value.appLock) appLock.lock()
            }
        })
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TrueCtrlApp).container
