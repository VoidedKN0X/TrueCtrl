package com.vknxworks.truectrl.data.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one app stands in an "Update all apps" run. */
enum class UpgradeState { QUEUED, RUNNING, DONE, FAILED, SKIPPED }

data class AppUpgrade(
    val name: String,
    /** The version installed when the run started. */
    val from: String,
    /** The version TrueNAS offers ("latest"), when it says. */
    val to: String?,
    val state: UpgradeState = UpgradeState.QUEUED,
    /** The upgrade job's progress, 0–100, while it runs. */
    val percent: Double? = null,
    /** The upgrade job's own progress text ("Pulling images", …). */
    val progressText: String? = null,
    val error: Throwable? = null,
)

/** One "Update all apps" run on one server. */
data class AppUpgradeRun(
    val serverId: String,
    val items: List<AppUpgrade>,
    val running: Boolean,
    /** Stop was asked for: the app being updated finishes, the rest are skipped. */
    val stopping: Boolean = false,
) {
    val done: Int get() = items.count { it.state == UpgradeState.DONE }
    val failed: Int get() = items.count { it.state == UpgradeState.FAILED }
    val skipped: Int get() = items.count { it.state == UpgradeState.SKIPPED }
    val finished: Int get() = items.count { it.state != UpgradeState.QUEUED && it.state != UpgradeState.RUNNING }

    /** 0–1 over the whole run: finished apps count fully, the running one by its job's percent. */
    val progress: Float
        get() {
            if (items.isEmpty()) return 1f
            val running = items.firstOrNull { it.state == UpgradeState.RUNNING }?.percent?.coerceIn(0.0, 100.0)?.div(100.0) ?: 0.0
            return ((finished + running) / items.size).toFloat().coerceIn(0f, 1f)
        }
}

object AppUpdates {
    /** Installed apps with an update available, by name. */
    fun upgradable(apps: List<AppDetail>): List<AppDetail> =
        apps.filter { it.upgradeAvailable }.sortedBy { it.name.lowercase() }

    /** The apps to update, in list order: [selected] names that still have an update. */
    fun plan(apps: List<AppDetail>, selected: Set<String>): List<AppUpgrade> =
        upgradable(apps).filter { it.name in selected }.map { AppUpgrade(it.name, it.version, it.latestVersion) }
}

/** Updates one app and reports its job's progress; throws when the update fails. */
typealias AppUpgrader = suspend (name: String, onProgress: (JobInfo) -> Unit) -> Unit

/**
 * Runs "Update all apps": one app after another (TrueNAS pulls images and redeploys each one, so doing
 * them in parallel only makes every one slower), going on after a failure. It runs in the app's scope,
 * so leaving the screen doesn't stop it; [stop] lets the app being updated finish and skips the rest.
 */
class AppUpdateRunner(private val scope: CoroutineScope) {
    private val _run = MutableStateFlow<AppUpgradeRun?>(null)
    val run: StateFlow<AppUpgradeRun?> = _run.asStateFlow()
    private var job: Job? = null

    val busy: Boolean get() = _run.value?.running == true

    /** Starts a run; false when one is already running. */
    @Synchronized
    fun start(serverId: String, plan: List<AppUpgrade>, upgrade: AppUpgrader): Boolean {
        if (busy || plan.isEmpty()) return false
        _run.value = AppUpgradeRun(serverId, plan, running = true)
        job = scope.launch { execute(upgrade) }
        return true
    }

    /** Runs the plan in the calling coroutine (also used by the tests). */
    internal suspend fun execute(upgrade: AppUpgrader) {
        try {
            for (index in _run.value?.items?.indices ?: IntRange.EMPTY) {
                if (_run.value?.stopping == true) {
                    set(index) { it.copy(state = UpgradeState.SKIPPED) }
                    continue
                }
                set(index) { it.copy(state = UpgradeState.RUNNING) }
                val name = _run.value?.items?.get(index)?.name ?: continue
                try {
                    upgrade(name) { p ->
                        set(index) { it.copy(percent = p.percent, progressText = p.progressText) }
                    }
                    set(index) { it.copy(state = UpgradeState.DONE, percent = 100.0) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    set(index) { it.copy(state = UpgradeState.FAILED, error = e) }
                }
            }
        } finally {
            _run.update { r ->
                r?.copy(
                    running = false,
                    items = r.items.map { if (it.state == UpgradeState.QUEUED || it.state == UpgradeState.RUNNING) it.copy(state = UpgradeState.SKIPPED) else it },
                )
            }
        }
    }

    /** The app being updated finishes on TrueNAS; the ones after it are skipped. */
    fun stop() {
        _run.update { it?.copy(stopping = true) }
    }

    /** Forgets a finished run (Done). */
    @Synchronized
    fun clear() {
        if (!busy) _run.value = null
    }

    private fun set(index: Int, change: (AppUpgrade) -> AppUpgrade) {
        _run.update { r -> r?.copy(items = r.items.mapIndexed { i, a -> if (i == index) change(a) else a }) }
    }

    /** Only for tests: a run set up without starting it. */
    internal fun prepare(serverId: String, plan: List<AppUpgrade>) {
        _run.value = AppUpgradeRun(serverId, plan, running = true)
    }
}
