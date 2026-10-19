package com.vknxworks.truectrl.data.notify

import android.content.Context
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.overview.Attention
import com.vknxworks.truectrl.data.overview.AttentionItem
import com.vknxworks.truectrl.data.overview.OverviewReader
import com.vknxworks.truectrl.data.overview.OverviewSnapshot
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.overview.TaskKind
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.server.Server
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.overview.alertText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** What a server looked like at the last check, so only changes are notified. Kept per server. */
@Serializable
data class CheckState(
    /** Conditions that were true last time (and have been notified). */
    val active: Set<String> = emptySet(),
    /** Alerts (by uuid) already seen. */
    val seenAlerts: Set<String> = emptySet(),
    /** Failed task runs already seen ("kind:id:time"). */
    val seenFailures: Set<String> = emptySet(),
    /** The update version last notified. */
    val notifiedUpdate: String? = null,
    /** Checks in a row that couldn't reach the server. */
    val unreachableCount: Int = 0,
    val lastCheckAt: Long = 0,
    /** "ok" or "failed". */
    val lastResult: String = "",
)

/** Result of one check: notifications to show, keys whose notifications to remove, and the new state. */
class CheckResult(val post: List<Alert>, val cancel: Set<String>, val state: CheckState)

data class CheckConfig(
    val notify: NotifySettings,
    val poolWarnPercent: Int,
    /** Whether several servers are saved; then titles name the server. */
    val manyServers: Boolean,
)

/**
 * One background check of one server. Conditions (an unhealthy or full pool, scrub errors, a
 * crashed app, the server unreachable) are notified when they start and removed when they end;
 * events (a new TrueNAS alert, a failed task, an update) are notified once. The first check of a
 * server only remembers what's already there, so adding a server doesn't flood the phone.
 */
class Checker(private val context: Context) {

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    suspend fun check(server: Server, connection: Connection, config: CheckConfig, prev: CheckState): CheckResult {
        val now = System.currentTimeMillis()
        connection.start()
        val access = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            connection.access.first { it.connected || it.error?.needsUser == true }
        }
        val caps = access?.capabilities
        if (access?.connected != true || caps == null) {
            return unreachable(server, config, prev, access?.error ?: TnError.Unreachable(null), now)
        }
        val snapshot = try {
            OverviewReader.read(connection, caps, OverviewSnapshot(), checkLegacyUpdate = false)
        } catch (e: TnError) {
            return unreachable(server, config, prev, e, now)
        }
        return evaluate(server, snapshot, config, prev, now)
    }

    /** Turns one snapshot into notifications. */
    fun evaluate(server: Server, snapshot: OverviewSnapshot, config: CheckConfig, prev: CheckState, now: Long): CheckResult {
        val n = config.notify
        val first = prev.lastCheckAt == 0L
        val prefix = if (config.manyServers) "${server.name}: " else ""
        val key = { k: String -> "${server.id}:$k" }
        val conditions = LinkedHashMap<String, Alert>()
        val events = mutableListOf<Alert>()

        // Conditions, from the same rules as the Overview's attention list.
        for (item in Attention.compute(snapshot, config.poolWarnPercent, now)) {
            when (item) {
                is AttentionItem.PoolUnhealthy -> if (n.on(NotifyEvent.POOL_UNHEALTHY)) {
                    conditions[key("pool:${item.pool.name}:health")] = Alert(
                        key("pool:${item.pool.name}:health"), NotifyEvent.POOL_UNHEALTHY, item.severity,
                        prefix + s(R.string.nt_pool_unhealthy, item.pool.name, item.pool.status),
                        item.pool.statusDetail ?: s(R.string.nt_pool_unhealthy_text),
                    )
                }
                is AttentionItem.PoolFull -> if (n.on(NotifyEvent.POOL_FULL)) {
                    conditions[key("pool:${item.pool.name}:full")] = Alert(
                        key("pool:${item.pool.name}:full"), NotifyEvent.POOL_FULL, item.severity,
                        prefix + s(R.string.nt_pool_full, item.pool.name, (item.fraction * 100).toInt()),
                        s(R.string.nt_pool_full_text),
                    )
                }
                is AttentionItem.ScrubErrors -> if (n.on(NotifyEvent.SCRUB_ERRORS)) {
                    conditions[key("pool:${item.pool.name}:scrub")] = Alert(
                        key("pool:${item.pool.name}:scrub"), NotifyEvent.SCRUB_ERRORS, Severity.CRITICAL,
                        prefix + s(R.string.nt_scrub_errors, item.pool.name, item.errors),
                        s(R.string.nt_scrub_errors_text),
                    )
                }
                is AttentionItem.AppCrashed -> if (n.on(NotifyEvent.APP_CRASHED)) {
                    conditions[key("app:${item.app.name}")] = Alert(
                        key("app:${item.app.name}"), NotifyEvent.APP_CRASHED, Severity.CRITICAL,
                        prefix + s(R.string.nt_app_crashed, item.app.name),
                        s(R.string.nt_app_crashed_text),
                    )
                }
                else -> Unit
            }
        }

        // New TrueNAS alerts. With push on, TrueNAS sends them itself, so they're only remembered here.
        val liveAlerts = snapshot.alerts.filter { !it.dismissed }
        if (!first && !n.push) {
            for (a in liveAlerts) {
                if (a.uuid in prev.seenAlerts || a.severity.ordinal > n.minAlertLevel.ordinal) continue
                val smart = a.klass.contains("SMART", ignoreCase = true) || a.klass.contains("Smartd", ignoreCase = true) ||
                    a.klass.startsWith("DiskTemperature")
                val event = if (smart) NotifyEvent.SMART else NotifyEvent.NEW_ALERT
                if (!n.on(event)) continue
                val text = alertText(a.text)
                events += Alert(
                    key("alert:${a.uuid}"), event, a.severity,
                    prefix + text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(120).ifBlank { s(R.string.nt_alert_title) },
                    text.take(600),
                )
            }
        }
        // An alert that's gone (or dismissed) takes its notification with it.
        val goneAlerts = prev.seenAlerts - liveAlerts.map { it.uuid }.toSet()

        // Failed tasks, once per failed run.
        val failures = snapshot.tasks.filter { it.enabled && it.failed }
        val failureKeys = failures.associateBy { "${it.kind}:${it.id}:${it.atMs ?: 0}" }
        if (!first && n.on(NotifyEvent.TASK_FAILED)) {
            failureKeys.filterKeys { it !in prev.seenFailures }.forEach { (k, t) ->
                val kind = s(
                    when (t.kind) {
                        TaskKind.REPLICATION -> R.string.task_replication
                        TaskKind.SNAPSHOT -> R.string.task_snapshot
                        TaskKind.CLOUD_SYNC -> R.string.task_cloud_sync
                        TaskKind.RSYNC -> R.string.task_rsync
                    },
                )
                events += Alert(
                    key("task:$k"), NotifyEvent.TASK_FAILED, Severity.CRITICAL,
                    prefix + s(R.string.att_task_failed, kind, t.name),
                    t.error ?: s(R.string.nt_task_failed_text),
                )
            }
        }

        // An update, once per version.
        val update = snapshot.update?.takeIf { it.available }?.version
        var notifiedUpdate = prev.notifiedUpdate
        if (update != null && update != prev.notifiedUpdate) {
            if (!first && n.on(NotifyEvent.UPDATES)) {
                events += Alert(
                    key("update"), NotifyEvent.UPDATES, Severity.INFO,
                    prefix + s(R.string.att_update_version, update),
                    s(R.string.nt_update_text),
                )
            }
            notifiedUpdate = update
        }

        val newConditions = conditions.filterKeys { it !in prev.active }.values
        val ended = prev.active - conditions.keys
        return CheckResult(
            post = newConditions.toList() + events,
            cancel = ended + goneAlerts.map { key("alert:$it") } + key("unreachable"),
            state = CheckState(
                active = conditions.keys,
                seenAlerts = liveAlerts.map { it.uuid }.toSet(),
                seenFailures = failureKeys.keys,
                notifiedUpdate = notifiedUpdate,
                unreachableCount = 0,
                lastCheckAt = now,
                lastResult = "ok",
            ),
        )
    }

    /** Notified after two failed checks in a row, so a single blip doesn't wake anyone. */
    private fun unreachable(server: Server, config: CheckConfig, prev: CheckState, error: TnError, now: Long): CheckResult {
        val count = prev.unreachableCount + 1
        val k = "${server.id}:unreachable"
        val post = if ((count >= 2 || error.needsUser) && k !in prev.active && config.notify.on(NotifyEvent.UNREACHABLE)) {
            listOf(
                Alert(
                    k, NotifyEvent.UNREACHABLE, Severity.WARNING,
                    s(R.string.nt_unreachable, server.name),
                    errorMessage(context, error, server.host),
                ),
            )
        } else {
            emptyList()
        }
        return CheckResult(
            post = post,
            cancel = emptySet(),
            state = prev.copy(
                active = if (post.isNotEmpty()) prev.active + k else prev.active,
                unreachableCount = count,
                lastCheckAt = now,
                lastResult = "failed",
            ),
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
