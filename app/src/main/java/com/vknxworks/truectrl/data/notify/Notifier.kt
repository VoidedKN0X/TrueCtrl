package com.vknxworks.truectrl.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.truectrl.MainActivity
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.overview.Severity

/** One notification to show. [key] identifies it, so it can be replaced or removed later. */
data class Alert(
    val key: String,
    val event: NotifyEvent?,
    val severity: Severity,
    val title: String,
    val text: String,
)

/** Notification channels and posting. Channels let the user pick sound and importance per kind. */
object Notifier {
    const val CH_CRITICAL = "critical"
    const val CH_WARNING = "warnings"
    const val CH_INFO = "info"
    const val CH_TRUENAS = "truenas"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_CRITICAL, context.getString(R.string.ch_critical), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.ch_critical_desc)
                },
                NotificationChannel(CH_WARNING, context.getString(R.string.ch_warning), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.ch_warning_desc)
                },
                NotificationChannel(CH_INFO, context.getString(R.string.ch_info), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.ch_info_desc)
                },
                NotificationChannel(CH_TRUENAS, context.getString(R.string.ch_truenas), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.ch_truenas_desc)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun idFor(key: String) = key.hashCode()

    fun post(context: Context, alert: Alert, channel: String = channelFor(alert.severity)) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            idFor(alert.key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // On a locked phone only this redacted version shows: no server, pool, disk or app names.
        val redacted = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.nt_public_text))
            .build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            // Accent for the icon and app name; values-night holds the dark-mode shade.
            .setColor(context.getColor(R.color.notification_accent))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(if (alert.severity == Severity.CRITICAL) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setPriority(
                when (alert.severity) {
                    Severity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
                    Severity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
                    Severity.INFO -> NotificationCompat.PRIORITY_LOW
                },
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alert.key, idFor(alert.key), n)
        } catch (_: SecurityException) {
        }
    }

    /** Removes every notification about a server, when that server is removed from the app. */
    fun cancelServer(context: Context, serverId: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.activeNotifications.filter { it.tag?.startsWith("$serverId:") == true }.forEach { nm.cancel(it.tag, it.id) }
        }
    }

    /** Removes a notification whose condition is gone (e.g. a pool that's healthy again). */
    fun cancel(context: Context, key: String) {
        NotificationManagerCompat.from(context).cancel(key, idFor(key))
    }

    fun channelFor(severity: Severity) = when (severity) {
        Severity.CRITICAL -> CH_CRITICAL
        Severity.WARNING -> CH_WARNING
        Severity.INFO -> CH_INFO
    }
}
