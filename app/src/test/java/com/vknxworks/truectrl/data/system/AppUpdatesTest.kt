package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    /** A trimmed app.query answer (AppEntry), as recorded from TrueNAS 25.10. */
    private val appQuery = """
        [
          {"name": "jellyfin", "id": "jellyfin", "state": "RUNNING", "version": "1.2.3", "human_version": "10.10.7_1.2.3",
           "upgrade_available": true, "latest_version": "1.2.4", "image_updates_available": false, "custom_app": false},
          {"name": "Immich", "id": "immich", "state": "RUNNING", "human_version": "v1.135.0_1.4.1",
           "upgrade_available": true, "latest_version": "1.4.2"},
          {"name": "paperless", "id": "paperless", "state": "STOPPED", "human_version": "2.17.1_1.0.0",
           "upgrade_available": false, "latest_version": null},
          {"state": "RUNNING"}
        ]
    """.trimIndent()

    private val apps = TnJson.parseToJsonElement(appQuery).let { j -> (j as kotlinx.serialization.json.JsonArray).mapNotNull(AppDetail::from) }

    @Test
    fun onlyAppsWithAnUpdateSortedByName() {
        assertEquals(listOf("Immich", "jellyfin"), AppUpdates.upgradable(apps).map { it.name })
    }

    @Test
    fun planKeepsSelectedAppsThatStillHaveAnUpdate() {
        val plan = AppUpdates.plan(apps, setOf("jellyfin", "paperless", "gone"))
        assertEquals(listOf("jellyfin"), plan.map { it.name })
        assertEquals("10.10.7_1.2.3", plan.single().from)
        assertEquals("1.2.4", plan.single().to)
    }

    @Test
    fun jobProgressIsRead() {
        val job = TnJson.parseToJsonElement(
            """{"id": 812, "method": "app.upgrade", "state": "RUNNING", "progress": {"percent": 40, "description": "Pulling images"}, "abortable": false}""",
        )
        val info = JobInfo.from(job)!!
        assertEquals(40.0, info.percent!!, 0.0)
        assertEquals("Pulling images", info.progressText)
        assertTrue(info.active)
    }

    @Test
    fun runsOneAfterAnotherAndGoesOnAfterAFailure() = runBlocking {
        val runner = AppUpdateRunner(CoroutineScope(Dispatchers.Unconfined))
        val plan = listOf(AppUpgrade("a", "1", "2"), AppUpgrade("b", "1", "2"), AppUpgrade("c", "1", "2"))
        runner.prepare("srv", plan)
        val order = mutableListOf<String>()
        runner.execute { name, progress ->
            order += name
            // Only one app runs at a time.
            assertEquals(1, runner.run.value!!.items.count { it.state == UpgradeState.RUNNING })
            progress(JobInfo(1, "app.upgrade", null, "RUNNING", 50.0, "Pulling", null, false, null, null))
            assertEquals(50.0, runner.run.value!!.items.first { it.name == name }.percent!!, 0.0)
            if (name == "b") throw TnError.Call("EFAULT", "image pull failed")
        }
        val run = runner.run.value!!
        assertEquals(listOf("a", "b", "c"), order)
        assertEquals(listOf(UpgradeState.DONE, UpgradeState.FAILED, UpgradeState.DONE), run.items.map { it.state })
        assertFalse(run.running)
        assertEquals(2, run.done)
        assertEquals(1, run.failed)
        assertEquals(1f, run.progress, 0f)
        assertTrue(run.items[1].error is TnError.Call)
    }

    @Test
    fun stopSkipsTheRest() = runBlocking {
        val runner = AppUpdateRunner(CoroutineScope(Dispatchers.Unconfined))
        runner.prepare("srv", listOf(AppUpgrade("a", "1", "2"), AppUpgrade("b", "1", "2"), AppUpgrade("c", "1", "2")))
        runner.execute { name, _ -> if (name == "a") runner.stop() }
        assertEquals(listOf(UpgradeState.DONE, UpgradeState.SKIPPED, UpgradeState.SKIPPED), runner.run.value!!.items.map { it.state })
        assertEquals(2, runner.run.value!!.skipped)
    }

    @Test
    fun onlyOneRunAtATimeAndClearAfterwards() {
        val runner = AppUpdateRunner(CoroutineScope(Dispatchers.Unconfined))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        assertTrue(runner.start("srv", listOf(AppUpgrade("a", "1", "2")), { _, _ -> gate.await() }))
        assertFalse(runner.start("srv", listOf(AppUpgrade("b", "1", "2")), { _, _ -> }))
        runner.clear()
        assertTrue(runner.run.value!!.running)
        gate.complete(Unit)
        assertFalse(runner.run.value!!.running)
        runner.clear()
        assertNull(runner.run.value)
        assertFalse(runner.start("srv", emptyList(), { _, _ -> }))
    }

    @Test
    fun progressCountsTheRunningApp() {
        val run = AppUpgradeRun(
            "srv",
            listOf(AppUpgrade("a", "1", "2", UpgradeState.DONE), AppUpgrade("b", "1", "2", UpgradeState.RUNNING, percent = 50.0)),
            running = true,
        )
        assertEquals(0.75f, run.progress, 0.001f)
    }
}
