package com.vknxworks.truectrl.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.server.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/** Saved [DiskHealthState] per server, in no-backup storage. */
object DiskHealthStore {
    private val _last = MutableStateFlow<Map<String, DiskHealthState>>(emptyMap())
    val last: StateFlow<Map<String, DiskHealthState>> = _last.asStateFlow()

    private fun file(context: Context, serverId: String) =
        File(File(context.noBackupFilesDir, "disk-health").apply { mkdirs() }, "$serverId.json")

    suspend fun read(context: Context, serverId: String): DiskHealthState = withContext(Dispatchers.IO) {
        val s = runCatching { TnJson.decodeFromString(DiskHealthState.serializer(), file(context, serverId).readText()) }.getOrNull() ?: DiskHealthState()
        _last.value = _last.value + (serverId to s)
        s
    }

    suspend fun write(context: Context, serverId: String, state: DiskHealthState) = withContext(Dispatchers.IO) {
        runCatching { file(context, serverId).writeText(TnJson.encodeToString(DiskHealthState.serializer(), state)) }
        _last.value = _last.value + (serverId to state)
    }

    fun forget(context: Context, serverId: String) {
        runCatching { file(context, serverId).delete() }
        _last.value = _last.value - serverId
    }
}

/**
 * The disk-health check: every few minutes to once a day (the user's choice) while it's on, with a
 * network connection, over its own short-lived read-only connection to each server. New problems are
 * notified once (and posted to the user's ntfy topic when that's on); a notification goes away when its
 * problem does. Unlike the general background check, problems already there when it's turned on are
 * notified too: a failing disk is worth hearing about straight away.
 */
class DiskHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.notifySettings.current()
        if (!settings.diskHealth && !inputData.getBoolean(NotifyWorker.KEY_FORCE, false)) return Result.success()
        val servers = c.sessions.servers.value
        val ntfy = if (settings.ntfyPost) Ntfy.target(settings.ntfyServer, settings.ntfyTopic, c.serverStore.ntfyToken(), settings.ntfyCertPin).first else null
        for (server in servers) {
            val prev = DiskHealthStore.read(applicationContext, server.id)
            val connection = c.sessions.backgroundConnection(server)
            val input = runCatching {
                connection.start()
                val access = withTimeoutOrNull(30_000) { connection.access.first { it.connected || it.error?.needsUser == true } }
                val caps = access?.capabilities
                if (access?.connected == true && caps != null) DiskHealth.read(connection, caps) else null
            }.getOrNull()
            connection.stop()
            val now = System.currentTimeMillis()
            if (input == null) {
                DiskHealthStore.write(applicationContext, server.id, prev.copy(lastCheckAt = now, lastResult = "failed"))
                continue
            }
            val findings = DiskHealth.findings(input)
            val (fresh, gone) = DiskHealth.diff(prev.active, findings)
            gone.forEach { Notifier.cancel(applicationContext, notificationKey(server, it)) }
            val many = servers.size > 1
            fresh.forEach { f -> Notifier.post(applicationContext, alertFor(applicationContext, server, f, many)) }
            if (fresh.isNotEmpty() && ntfy != null) {
                // Best effort: the phone's own notifications are already there if the ntfy server can't be reached.
                runCatching {
                    Ntfy.publish(
                        c.http,
                        ntfy,
                        applicationContext.getString(R.string.dh_ntfy_title, server.name),
                        fresh.joinToString("\n") { line(applicationContext, it) },
                        fresh.minOf { it.severity },
                    )
                }
            }
            DiskHealthStore.write(applicationContext, server.id, DiskHealthState(findings.map { it.key }.toSet(), now, "ok"))
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "truectrl-disk-health"
        private const val ONCE = "truectrl-disk-health-now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun notificationKey(server: Server, findingKey: String) = "${server.id}:disk:$findingKey"

        /** One finding as a sentence, for the notification and the ntfy message. */
        fun line(context: Context, f: DiskFinding): String = when (f.problem) {
            DiskProblem.POOL_STATUS -> context.getString(R.string.dh_pool_status, f.subject, f.detail.orEmpty())
            DiskProblem.DISK_STATE -> context.getString(R.string.dh_disk_state, f.subject, f.pool.orEmpty(), f.detail.orEmpty())
            DiskProblem.DISK_ERRORS -> {
                val (r, w, ck) = f.detail.orEmpty().split('/').map { it.toLongOrNull() ?: 0 }.let { l -> Triple(l.getOrElse(0) { 0 }, l.getOrElse(1) { 0 }, l.getOrElse(2) { 0 }) }
                context.getString(R.string.dh_disk_errors, f.subject, f.pool.orEmpty(), r, w, ck)
            }
            DiskProblem.SMART_TEST -> context.getString(R.string.dh_smart_failed, f.subject, f.detail.orEmpty())
            DiskProblem.ALERT -> com.vknxworks.truectrl.ui.overview.alertText(f.detail.orEmpty())
                .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(200)
        }

        private fun alertFor(context: Context, server: Server, f: DiskFinding, manyServers: Boolean): Alert {
            val text = line(context, f)
            val title = context.getString(if (f.severity == Severity.CRITICAL) R.string.dh_title_critical else R.string.dh_title_warning)
            return Alert(
                key = notificationKey(server, f.key),
                event = null,
                severity = f.severity,
                title = if (manyServers) "${server.name}: $title" else title,
                text = text,
            )
        }

        /** Starts, changes or stops the periodic check to match [settings]. */
        fun schedule(context: Context, settings: NotifySettings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.diskHealth) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<DiskHealthWorker>(settings.diskHealthMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(PERIODIC)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One check now ("Check disks now"), even when the periodic check is off. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DiskHealthWorker>()
                .setConstraints(constraints)
                .setInputData(androidx.work.workDataOf(NotifyWorker.KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
