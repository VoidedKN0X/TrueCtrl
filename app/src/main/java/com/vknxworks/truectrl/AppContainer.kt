package com.vknxworks.truectrl

import android.content.Context
import com.vknxworks.truectrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container; one per process. Server connections, the TrueNAS JSON-RPC client and
 * pinned TLS are added with server setup.
 */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as TrueCtrlApp).container
