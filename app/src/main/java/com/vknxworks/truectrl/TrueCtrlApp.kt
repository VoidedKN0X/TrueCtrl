package com.vknxworks.truectrl

import android.app.Application
import com.vknxworks.truectrl.data.notify.Notifier
import com.vknxworks.truectrl.data.notify.NotifyWorker
import com.vknxworks.truectrl.data.notify.Push
import com.vknxworks.truectrl.data.notify.PushStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class TrueCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        val c = container
        // The home-screen widgets and the Quick Settings tile were removed: delete the summary of each
        // server (pool names, health and alert counts) they kept on the phone.
        c.appScope.launch(Dispatchers.IO) {
            runCatching { java.io.File(java.io.File(noBackupFilesDir, "cache"), "health.json").delete() }
            // Encryption at rest: seal the offline copies and check notes that were saved before it existed.
            runCatching { c.sealStoredFiles() }
        }
        c.appScope.launch {
            // Keep the background check and push registration in line with the settings.
            val first = c.notifySettings.current()
            NotifyWorker.schedule(this@TrueCtrlApp, first)
            com.vknxworks.truectrl.data.notify.DiskHealthWorker.schedule(this@TrueCtrlApp, first)
            Push.load(this@TrueCtrlApp, first.push)
            c.notifySettings.settings.drop(1).distinctUntilChanged().collect { s ->
                NotifyWorker.schedule(this@TrueCtrlApp, s)
                com.vknxworks.truectrl.data.notify.DiskHealthWorker.schedule(this@TrueCtrlApp, s)
                if (!s.push && Push.status.value !is PushStatus.Off) Push.unregister(this@TrueCtrlApp)
            }
        }
    }
}
