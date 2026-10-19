package com.vknxworks.truectrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.truectrl.data.server.ServerStore
import com.vknxworks.truectrl.data.server.Sessions
import com.vknxworks.truectrl.data.settings.SettingsRepository
import com.vknxworks.truectrl.data.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. Created on the main thread in [TrueCtrlApp]. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)

    /** Opt-in anonymous usage stats, with its own HTTP client (normal certificate checks, no TrueNAS pins). */
    val telemetry = Telemetry(context, settings, appScope)

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

    val serverStore = ServerStore(context)
    val sessions = Sessions(serverStore, http, appScope)

    init {
        // One live connection while the app is on screen; background checks come later with WorkManager.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = sessions.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = sessions.setForeground(false)
        })
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TrueCtrlApp).container
