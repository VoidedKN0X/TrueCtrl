package com.vknxworks.truectrl.data.api

/**
 * The central read-only check. In read-only mode every call goes through [allows] before it's sent
 * (see Connection.call), so read-only mode holds even if a screen forgets to hide an action.
 *
 * It fails closed: only methods known to just read are let through, by exact name or by a
 * read-only last segment (`pool.query`, `pool.dataset.get_instance`, `system.general.config`).
 * Anything else, including any method the app adds later that isn't listed here, is refused.
 * When adding a new read method that doesn't match, add it to [READ_METHODS].
 */
object ReadOnlyGate {
    /** Reads whose names don't follow the patterns below. */
    private val READ_METHODS = setOf(
        "auth.me",
        "core.get_methods",
        "core.ping",
        // Subscribing only asks TrueNAS to push changes; it changes nothing on the server.
        "core.subscribe",
        "core.unsubscribe",
        // 25.04's update check: asks the update server, changes nothing.
        "update.check_available",
        // The active and dismissed alerts (the Overview and the Alerts screen).
        "alert.list",
        // Sends a test alert through an alert service; changes nothing.
        "alertservice.test",
        // What deleting a dataset would affect (shown before asking).
        "pool.dataset.snapshot_count",
        "pool.dataset.attachments",
        "pool.dataset.processes",
        // Job progress and results.
        "core.get_jobs",
        // Disk temperatures and graphs.
        "disk.temperatures",
        "disk.temperature_agg",
        "reporting.get_data",
        "reporting.graphs",
        "reporting.netdata_get_data",
        "reporting.netdata_graphs",
        // SMART (TrueNAS 25.04 only).
        "smart.test.results",
        "disk.smart_attributes",
    )

    /** Last name segments that only read: `<namespace>.query`, `.get_instance`, `.config` and so on. */
    private val READ_SUFFIXES = setOf("query", "get_instance", "config", "info", "version", "status", "details", "summary")

    fun allows(method: String): Boolean {
        if (method in READ_METHODS) return true
        return method.substringAfterLast('.') in READ_SUFFIXES
    }
}
