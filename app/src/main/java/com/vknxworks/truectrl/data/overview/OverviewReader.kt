package com.vknxworks.truectrl.data.overview

import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.UpdateApi
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.server.Connection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement

/**
 * Reads everything the Overview and the background check need in one go: system, pools, alerts,
 * protection tasks, services, apps, scrub schedules, updates and a pending reboot. Methods this user
 * can't call, or this TrueNAS version doesn't have, are skipped. Shared by the Overview screen and
 * the background notification check.
 */
object OverviewReader {
    suspend fun read(c: Connection, caps: Capabilities, previous: OverviewSnapshot, checkLegacyUpdate: Boolean): OverviewSnapshot = coroutineScope {
        /** Calls [method] when this user may; null when not allowed or not on this version. */
        fun opt(method: String, vararg params: JsonElement) = async {
            if (!caps.has(method)) return@async null
            try {
                c.call(method, params.toList())
            } catch (e: TnError.Forbidden) {
                null
            } catch (e: TnError.MethodMissing) {
                null
            } catch (e: TnError.Call) {
                null
            }
        }

        val info = opt("system.info")
        val reboot = opt("system.reboot.info")
        val pools = opt("pool.query")
        val alerts = opt("alert.list")
        val scrubs = opt("pool.scrub.query")
        val replication = opt("replication.query")
        val snapshotTasks = opt("pool.snapshottask.query")
        val cloud = opt("cloudsync.query")
        val rsync = opt("rsynctask.query")
        val services = opt("service.query")
        val apps = opt("app.query")
        val update = async { readUpdate(c, caps, checkLegacyUpdate, previous.update) }

        val tasks = buildList {
            replication.await().arr()?.forEach { j ->
                TaskRun.fromState(TaskKind.REPLICATION, j, j["name"].str().orEmpty())?.let(::add)
            }
            snapshotTasks.await().arr()?.forEach { j ->
                TaskRun.fromState(TaskKind.SNAPSHOT, j, j["dataset"].str().orEmpty())?.let(::add)
            }
            cloud.await().arr()?.forEach { j ->
                TaskRun.fromJob(TaskKind.CLOUD_SYNC, j, j["description"].str()?.takeIf { it.isNotBlank() } ?: j["path"].str().orEmpty())?.let(::add)
            }
            rsync.await().arr()?.forEach { j ->
                TaskRun.fromJob(TaskKind.RSYNC, j, j["desc"].str()?.takeIf { it.isNotBlank() } ?: j["path"].str().orEmpty())?.let(::add)
            }
        }

        OverviewSnapshot(
            info = SystemInfo.from(info.await()) ?: previous.info,
            pools = pools.await().arr()?.mapNotNull(PoolInfo::from).orEmpty(),
            alerts = alerts.await().arr()?.mapNotNull(AlertInfo::from).orEmpty(),
            tasks = tasks,
            services = services.await().arr()?.mapNotNull(ServiceInfo::from).orEmpty(),
            apps = apps.await().arr()?.mapNotNull(AppInfo::from).orEmpty(),
            scrubs = scrubs.await().arr()?.mapNotNull(ScrubSchedule::from).orEmpty(),
            update = update.await(),
            rebootReasons = rebootReasons(reboot.await()),
        )
    }

    /**
     * 25.10 and later: update.status is cheap (TrueNAS checks in the background). 25.04: the update
     * check asks the update server, so it runs once per connection and then every 6 hours.
     */
    private suspend fun readUpdate(c: Connection, caps: Capabilities, checkLegacy: Boolean, previous: UpdateInfo?): UpdateInfo? =
        runCatching {
            when (caps.updateApi) {
                UpdateApi.PROFILES -> {
                    val r = c.call("update.status")
                    val version = r["status"]["new_version"]["version"].str()
                    UpdateInfo(available = version != null, version = version)
                }
                UpdateApi.LEGACY -> {
                    if (!checkLegacy && previous != null) return@runCatching previous
                    val r = c.call("update.check_available")
                    when (r["status"].str()) {
                        "AVAILABLE" -> UpdateInfo(available = true, version = r["version"].str())
                        "REBOOT_REQUIRED" -> UpdateInfo(applied = true)
                        else -> UpdateInfo()
                    }
                }
                UpdateApi.NONE -> null
            }
        }.getOrElse { previous }

}
