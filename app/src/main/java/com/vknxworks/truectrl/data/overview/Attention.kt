package com.vknxworks.truectrl.data.overview

enum class Severity { CRITICAL, WARNING, INFO }

/** One thing on the Overview's attention list. */
sealed interface AttentionItem {
    val severity: Severity

    /** An active (not dismissed) TrueNAS alert. SMART failures, disk temperatures and quotas arrive this way. */
    data class Alert(val alert: AlertInfo) : AttentionItem {
        override val severity get() = alert.severity
    }

    /** A few lower-level alerts folded into one line. */
    data class MoreAlerts(val count: Int) : AttentionItem {
        override val severity get() = Severity.INFO
    }

    data class PoolUnhealthy(val pool: PoolInfo) : AttentionItem {
        override val severity get() = if (pool.isOnline) Severity.WARNING else Severity.CRITICAL
    }

    data class PoolFull(val pool: PoolInfo, val fraction: Double, override val severity: Severity) : AttentionItem

    data class ScrubErrors(val pool: PoolInfo, val errors: Long) : AttentionItem {
        override val severity get() = Severity.CRITICAL
    }

    /** No finished scrub within the schedule's threshold plus a week of slack. */
    data class ScrubOverdue(val pool: PoolInfo, val lastMs: Long?) : AttentionItem {
        override val severity get() = Severity.WARNING
    }

    data class TaskFailed(val task: TaskRun) : AttentionItem {
        override val severity get() = Severity.CRITICAL
    }

    /** Set to start on boot, but not running. */
    data class ServiceStopped(val service: ServiceInfo) : AttentionItem {
        override val severity get() = Severity.WARNING
    }

    data class AppCrashed(val app: AppInfo) : AttentionItem {
        override val severity get() = Severity.CRITICAL
    }

    data class UpdateAvailable(val version: String?) : AttentionItem {
        override val severity get() = Severity.INFO
    }

    data class RebootPending(val reasons: List<String>) : AttentionItem {
        override val severity get() = Severity.WARNING
    }
}

object Attention {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Alerts below this many are listed one by one; info alerts beyond it are folded into one line. */
    private const val MAX_INFO_ALERTS = 2

    /**
     * Everything that needs a look, most serious first. [poolWarnPercent] is the user's pool usage
     * warning; critical from 95 % or 10 points above it, whichever is lower.
     */
    fun compute(s: OverviewSnapshot, poolWarnPercent: Int, now: Long = System.currentTimeMillis()): List<AttentionItem> {
        val items = mutableListOf<AttentionItem>()

        val alerts = s.alerts.filter { !it.dismissed }.sortedWith(compareBy<AlertInfo> { it.severity.ordinal }.thenByDescending { it.atMs ?: 0 })
        val (important, info) = alerts.partition { it.severity != Severity.INFO }
        important.forEach { items += AttentionItem.Alert(it) }
        info.take(MAX_INFO_ALERTS).forEach { items += AttentionItem.Alert(it) }
        if (info.size > MAX_INFO_ALERTS) items += AttentionItem.MoreAlerts(info.size - MAX_INFO_ALERTS)

        val critical = minOf(95, poolWarnPercent + 10)
        for (pool in s.pools) {
            if (!pool.isOnline || !pool.healthy) items += AttentionItem.PoolUnhealthy(pool)
            pool.usedFraction?.let { f ->
                val pct = f * 100
                when {
                    pct >= critical -> items += AttentionItem.PoolFull(pool, f, Severity.CRITICAL)
                    pct >= poolWarnPercent -> items += AttentionItem.PoolFull(pool, f, Severity.WARNING)
                }
            }
            val scan = pool.scan
            if (scan != null && scan.isScrub && scan.errors > 0) items += AttentionItem.ScrubErrors(pool, scan.errors)
            val schedule = s.scrubs.firstOrNull { it.enabled && (it.poolId == pool.id || it.poolName == pool.name) }
            if (schedule != null && scan?.isRunning != true) {
                val last = scan?.takeIf { it.isScrub && it.state == "FINISHED" }?.endMs
                val limit = (schedule.thresholdDays + 7) * DAY_MS
                if (last == null || now - last > limit) items += AttentionItem.ScrubOverdue(pool, last)
            }
        }

        s.tasks.filter { it.enabled && it.failed }.forEach { items += AttentionItem.TaskFailed(it) }
        s.services.filter { it.enable && !it.running }.forEach { items += AttentionItem.ServiceStopped(it) }
        s.apps.filter { it.state == "CRASHED" }.forEach { items += AttentionItem.AppCrashed(it) }
        if (s.rebootReasons.isNotEmpty() || s.update?.applied == true) items += AttentionItem.RebootPending(s.rebootReasons)
        s.update?.takeIf { it.available }?.let { items += AttentionItem.UpdateAvailable(it.version) }

        // Stable: the order above stays within each severity.
        return items.sortedBy { it.severity.ordinal }
    }
}
