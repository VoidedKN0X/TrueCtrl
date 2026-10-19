package com.vknxworks.truectrl.data.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.overview.Attention
import com.vknxworks.truectrl.data.overview.OverviewSnapshot
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.server.Server
import com.vknxworks.truectrl.ui.widget.AlertsWidget
import com.vknxworks.truectrl.ui.widget.HealthTile
import com.vknxworks.truectrl.ui.widget.PoolsWidget
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class PoolLine(val name: String, val status: String, val healthy: Boolean, val usedFraction: Double?)

/** What the widgets and the Quick Settings tile show for one server, saved after each refresh or check. */
@Serializable
data class HealthSummary(
    val serverId: String,
    val serverName: String,
    val pools: List<PoolLine> = emptyList(),
    /** Attention items by severity, as on the Overview. */
    val critical: Int = 0,
    val warning: Int = 0,
    /** Active (not dismissed) TrueNAS alerts. */
    val alerts: Int = 0,
    val alertsCritical: Int = 0,
    val poolWarnPercent: Int = 80,
    /** When the server was last read successfully. */
    val updatedAt: Long = 0,
    /** The last attempt to reach it failed (the numbers are from [updatedAt]). */
    val unreachable: Boolean = false,
)

@Serializable
private data class HealthFile(val servers: Map<String, HealthSummary> = emptyMap())

/**
 * The last known health of each server, for the home-screen widgets and the Quick Settings tile.
 * Kept in no-backup storage; it holds pool names and counts, never anything secret.
 */
object HealthStore {
    private val lock = Any()

    private fun file(context: Context) = File(File(context.noBackupFilesDir, "cache").apply { mkdirs() }, "health.json")

    private fun readAll(context: Context): HealthFile =
        runCatching { TnJson.decodeFromString(HealthFile.serializer(), file(context).readText()) }.getOrNull() ?: HealthFile()

    fun read(context: Context, serverId: String?): HealthSummary? = synchronized(lock) {
        val all = readAll(context).servers
        (serverId?.let { all[it] }) ?: all.values.maxByOrNull { it.updatedAt }
    }

    fun from(server: Server, snapshot: OverviewSnapshot, poolWarnPercent: Int, backupMaxAgeDays: Int, now: Long = System.currentTimeMillis()): HealthSummary {
        val items = Attention.compute(snapshot, poolWarnPercent, now, backupMaxAgeDays)
        val active = snapshot.alerts.filter { !it.dismissed }
        return HealthSummary(
            serverId = server.id,
            serverName = server.name.ifBlank { server.host },
            pools = snapshot.pools.map { PoolLine(it.name, it.status, it.isOnline && it.healthy, it.usedFraction) },
            critical = items.count { it.severity == Severity.CRITICAL },
            warning = items.count { it.severity == Severity.WARNING },
            alerts = active.size,
            alertsCritical = active.count { it.severity == Severity.CRITICAL },
            poolWarnPercent = poolWarnPercent,
            updatedAt = now,
        )
    }

    /** Saves a fresh summary and redraws the widgets and tile. */
    fun save(context: Context, summary: HealthSummary) {
        synchronized(lock) {
            val all = readAll(context)
            runCatching { file(context).writeText(TnJson.encodeToString(HealthFile.serializer(), all.copy(servers = all.servers + (summary.serverId to summary)))) }
        }
        refreshViews(context)
    }

    /** The server couldn't be reached: keep its last numbers, marked as old. */
    fun markUnreachable(context: Context, serverId: String) {
        synchronized(lock) {
            val all = readAll(context)
            val s = all.servers[serverId] ?: return
            runCatching { file(context).writeText(TnJson.encodeToString(HealthFile.serializer(), all.copy(servers = all.servers + (serverId to s.copy(unreachable = true))))) }
        }
        refreshViews(context)
    }

    fun forget(context: Context, serverId: String) {
        synchronized(lock) {
            val all = readAll(context)
            runCatching { file(context).writeText(TnJson.encodeToString(HealthFile.serializer(), all.copy(servers = all.servers - serverId))) }
        }
        refreshViews(context)
    }

    fun refreshViews(context: Context) {
        val app = context.applicationContext
        runCatching {
            val manager = AppWidgetManager.getInstance(app)
            listOf(PoolsWidget::class.java, AlertsWidget::class.java).forEach { cls ->
                val ids = manager.getAppWidgetIds(ComponentName(app, cls))
                if (ids.isNotEmpty()) {
                    when (cls) {
                        PoolsWidget::class.java -> PoolsWidget.draw(app, manager, ids)
                        else -> AlertsWidget.draw(app, manager, ids)
                    }
                }
            }
        }
        runCatching { TileService.requestListeningState(app, ComponentName(app, HealthTile::class.java)) }
    }
}
