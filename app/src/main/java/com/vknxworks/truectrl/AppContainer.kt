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
import com.vknxworks.truectrl.data.lock.LockVault
import com.vknxworks.truectrl.data.notify.CheckStore
import com.vknxworks.truectrl.data.notify.Notifier
import com.vknxworks.truectrl.ui.lock.AppLock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** The app lock's PIN, fingerprint key and the key that seals the saved sign-ins while locked. */
    val vault = LockVault(context, "truectrl")

    /** Read once, synchronously, so the app never shows unlocked for a moment at start. */
    val lockAtStart: Boolean = vault.enabled || runBlocking { runCatching { settings.stored.first().appLock }.getOrDefault(false) }

    val serverStore = ServerStore(context, vault).apply {
        sealPolicy = { vault.enabled && notifySettings.settings.value.let { !it.polling && !it.push } }
    }
    val sessions = Sessions(serverStore, http, appScope, lockedAtStart = lockAtStart)

    /** The app lock is on, either with a PIN or still the old one waiting for a PIN. */
    val lockEnabled: Boolean get() = vault.enabled || settings.settings.value.appLock

    /** Turns the app lock on with [pin]. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        settings.update { it.copy(appLock = true) }
        withContext(Dispatchers.IO) { serverStore.applySealing() }
        sessions.setLocked(false)
    }

    /** Turns the app lock off (after the PIN was confirmed): sign-ins go back to Keystore-only encryption. */
    suspend fun disableLock() {
        withContext(Dispatchers.IO) { serverStore.applySealing(seal = false) }
        settings.update { it.copy(appLock = false) }
        vault.disable()
    }

    /** "Forgot PIN?": the sealed sign-ins can't be read any more, so remove every server and start over. */
    suspend fun forgetEverything() {
        val ids = serverStore.servers.value.map { it.id }
        for (id in ids) {
            overview.forget(id)
            storage.forget(id)
            datasets.forget(id)
            protection.forget(id)
            sharing.forget(id)
            HealthStore.forget(context, id)
            CheckStore.forget(context, id)
            Notifier.cancelServer(context, id)
        }
        serverStore.removeAll()
        AppLock.markUnlocked()
        settings.update { it.copy(appLock = false) }
        vault.disable()
        sessions.setLocked(false)
    }

    /** Locked: wipe the key from memory and close the connection. */
    fun onLocked() {
        vault.close()
        sessions.setLocked(true)
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        // Background checks may have been turned on or off since the secrets were last sealed.
        appScope.launch { runCatching { serverStore.applySealing() } }
        sessions.setLocked(false)
    }
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
        AppLock.onLocked = { onLocked() }
        // One live connection while the app is on screen; background checks run in NotifyWorker.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(lockEnabled, settings.settings.value.lockTimeout.millis)
                sessions.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                sessions.setForeground(false)
                AppLock.onBackground(lockEnabled, settings.settings.value.lockTimeout.millis)
            }
        })
        // Background checks sign in while the app is locked, so turning them on unseals the saved
        // sign-ins and turning them off seals them again (only possible while unlocked).
        appScope.launch {
            notifySettings.settings.map { it.polling || it.push }.distinctUntilChanged().drop(1).collect {
                if (vault.isOpen) runCatching { serverStore.applySealing() }
            }
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TrueCtrlApp).container
