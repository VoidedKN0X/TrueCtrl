package com.vknxworks.truectrl.data.api

/** Which update API a server has: 25.04's trains, or the profiles API from 25.10 on. */
enum class UpdateApi { LEGACY, PROFILES, NONE }

/**
 * What the signed-in user can do on this server, and which method names its TrueNAS version uses.
 * This is the compatibility layer: screens ask here instead of hard-coding a version's names, and
 * hide a feature when its method is missing ([methods] only lists what this user may call).
 */
data class Capabilities(
    val version: TnVersion?,
    /** Public methods this user may call, from core.get_methods for the namespaces the app uses. */
    val methods: Set<String>,
    /** The user's roles from auth.me, already expanded by TrueNAS (FULL_ADMIN includes every role). */
    val roles: Set<String>,
    /** The user's privilege allows the web shell. */
    val webShell: Boolean,
) {
    fun has(method: String): Boolean = method in methods

    fun hasRole(role: String): Boolean = FULL_ADMIN in roles || role in roles

    fun hasAllRoles(vararg role: String): Boolean = role.all { hasRole(it) }

    val isFullAdmin: Boolean get() = FULL_ADMIN in roles

    /** Can't change anything at all: suggest read-only mode. */
    val isReadOnly: Boolean get() = !isFullAdmin && roles.none { it.endsWith("_WRITE") || it.endsWith("_DELETE") }

    /** Nothing to see: the user has no read roles. */
    val isEmpty: Boolean get() = roles.isEmpty()

    /** `zfs.snapshot` on 25.04, `pool.snapshot` from 25.10 on; null when this user can't see snapshots. */
    val snapshotNamespace: String?
        get() = when {
            has("pool.snapshot.query") -> "pool.snapshot"
            has("zfs.snapshot.query") -> "zfs.snapshot"
            else -> null
        }

    /** Scrub and resilver progress event: renamed from `zfs.pool.scan` to `pool.scan` in 26. */
    val scanEvent: String get() = if (version?.atLeast(26) == true) "pool.scan" else "zfs.pool.scan"

    /** `service.control(verb, service)` from 25.10 on; 25.04 has `service.start/stop/restart`. */
    val hasServiceControl: Boolean get() = has("service.control")

    val updateApi: UpdateApi
        get() = when {
            has("update.status") -> UpdateApi.PROFILES
            has("update.check_available") -> UpdateApi.LEGACY
            else -> UpdateApi.NONE
        }

    /** SMART attributes, test results and manual tests: 25.04 only (25.10 removed them from the API). */
    val hasSmartApi: Boolean get() = has("smart.test.query")

    companion object {
        const val FULL_ADMIN = "FULL_ADMIN"

        /**
         * Namespaces whose methods are listed at connect time. core.get_methods returns full method
         * docs, so asking per namespace keeps the download small.
         */
        val NAMESPACES = listOf(
            "system", "system.reboot", "pool", "pool.dataset", "pool.scrub", "pool.snapshot", "zfs.snapshot", "pool.snapshottask",
            "disk", "smart.test", "replication", "cloudsync", "rsynctask", "sharing.smb", "sharing.nfs",
            "service", "smb", "app", "alert", "alertservice", "update", "interface", "reporting",
        )
    }
}
