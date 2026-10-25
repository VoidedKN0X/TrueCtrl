package com.vknxworks.truectrl.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.vknxworks.truectrl.MainActivity
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.widget.HealthStore
import com.vknxworks.truectrl.data.widget.HealthSummary
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vknxworks.truectrl.ui.theme.CtrlColors

/**
 * Health colours as on the Overview (see ui.theme.Health), from the Signal palettes: a light and a
 * night value, so the launcher switches them with the phone's dark / light setting.
 */
internal class DayNight(val day: Int, val night: Int)

internal object WidgetColors {
    private fun pair(light: Color, dark: Color) = DayNight(light.toArgb(), dark.toArgb())
    val OK = pair(CtrlColors.Light.AccentText, CtrlColors.Dark.AccentText)
    val WARN = pair(CtrlColors.Light.Warn, CtrlColors.Dark.Warn)
    val CRITICAL = pair(CtrlColors.Light.Error, CtrlColors.Dark.Error)
    val IDLE = pair(CtrlColors.Light.Inactive, CtrlColors.Dark.Inactive)
}

internal fun RemoteViews.setColor(viewId: Int, method: String, c: DayNight) = setColorInt(viewId, method, c.day, c.night)

/** The summary for the server the app has selected, or the most recent one. */
internal fun currentSummary(context: Context): HealthSummary? {
    val activeId = runCatching { context.container.serverStore.activeId.value }.getOrNull()
    return HealthStore.read(context, activeId)
}

internal fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** "All good", "2 problems, 1 critical" or "Can't reach it, numbers from 3 hours ago". */
internal fun statusLine(context: Context, s: HealthSummary): String {
    val problems = s.critical + s.warning
    val base = when {
        problems == 0 -> context.getString(R.string.widget_all_good)
        s.critical > 0 -> context.resources.getQuantityString(R.plurals.widget_problems_critical, problems, problems, s.critical)
        else -> context.resources.getQuantityString(R.plurals.widget_problems, problems, problems)
    }
    if (!s.unreachable) return base
    val age = DateUtils.getRelativeTimeSpanString(s.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return context.getString(R.string.widget_unreachable, age)
}

internal fun healthColor(s: HealthSummary): DayNight = when {
    s.unreachable -> WidgetColors.IDLE
    s.critical > 0 -> WidgetColors.CRITICAL
    s.warning > 0 -> WidgetColors.WARN
    else -> WidgetColors.OK
}

/** Pool health and usage of the selected server. */
class PoolsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = draw(context, manager, ids)

    companion object {
        private val ROWS = listOf(
            listOf(R.id.pool_row_1, R.id.pool_name_1, R.id.pool_pct_1, R.id.pool_bar_1),
            listOf(R.id.pool_row_2, R.id.pool_name_2, R.id.pool_pct_2, R.id.pool_bar_2),
            listOf(R.id.pool_row_3, R.id.pool_name_3, R.id.pool_pct_3, R.id.pool_bar_3),
            listOf(R.id.pool_row_4, R.id.pool_name_4, R.id.pool_pct_4, R.id.pool_bar_4),
        )

        fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = currentSummary(context)
            val views = RemoteViews(context.packageName, R.layout.widget_pools)
            views.setOnClickPendingIntent(android.R.id.background, openApp(context))
            if (s == null) {
                views.setTextViewText(R.id.server_name, context.getString(R.string.app_name))
                views.setTextViewText(R.id.status, context.getString(R.string.widget_no_data))
                views.setColor(R.id.health_icon, "setColorFilter", WidgetColors.IDLE)
                ROWS.forEach { views.setViewVisibility(it[0], View.GONE) }
            } else {
                views.setTextViewText(R.id.server_name, s.serverName)
                views.setTextViewText(R.id.status, statusLine(context, s))
                views.setColor(R.id.health_icon, "setColorFilter", healthColor(s))
                ROWS.forEachIndexed { i, (row, name, pct, bar) ->
                    val pool = s.pools.getOrNull(i)
                    if (pool == null) {
                        views.setViewVisibility(row, View.GONE)
                        return@forEachIndexed
                    }
                    views.setViewVisibility(row, View.VISIBLE)
                    views.setTextViewText(name, if (pool.healthy) pool.name else "${pool.name} · ${pool.status}")
                    val percent = pool.usedFraction?.let { (it * 100).roundToInt().coerceIn(0, 100) }
                    views.setTextViewText(pct, percent?.let { context.getString(R.string.percent, it) } ?: "")
                    views.setProgressBar(bar, 100, percent ?: 0, false)
                    val critical = minOf(95, s.poolWarnPercent + 10)
                    val color = when {
                        !pool.healthy -> WidgetColors.CRITICAL
                        percent != null && percent >= critical -> WidgetColors.CRITICAL
                        percent != null && percent >= s.poolWarnPercent -> WidgetColors.WARN
                        else -> null
                    }
                    if (color != null) {
                        views.setColorStateList(bar, "setProgressTintList", ColorStateList.valueOf(color.day), ColorStateList.valueOf(color.night))
                    } else {
                        views.setColorStateList(bar, "setProgressTintList", context.getColorStateList(R.color.widget_accent))
                    }
                }
            }
            manager.updateAppWidget(ids, views)
        }
    }
}

/** The number of active TrueNAS alerts on the selected server. */
class AlertsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = draw(context, manager, ids)

    companion object {
        fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = currentSummary(context)
            val views = RemoteViews(context.packageName, R.layout.widget_alerts)
            views.setOnClickPendingIntent(android.R.id.background, openApp(context))
            if (s == null) {
                views.setTextViewText(R.id.alert_count, "–")
                views.setTextViewText(R.id.alert_label, context.getString(R.string.widget_no_data_short))
                views.setTextViewText(R.id.alert_server, context.getString(R.string.app_name))
            } else {
                views.setTextViewText(R.id.alert_count, if (s.unreachable) "?" else s.alerts.toString())
                views.setColor(
                    R.id.alert_count,
                    "setTextColor",
                    when {
                        s.unreachable -> WidgetColors.IDLE
                        s.alertsCritical > 0 -> WidgetColors.CRITICAL
                        s.alerts > 0 -> WidgetColors.WARN
                        else -> WidgetColors.OK
                    },
                )
                views.setTextViewText(R.id.alert_label, context.resources.getQuantityString(R.plurals.widget_alerts, s.alerts))
                views.setTextViewText(R.id.alert_server, s.serverName)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
