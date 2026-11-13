package com.vknxworks.truectrl.data.server

import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.str
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Many TrueNAS actions run as jobs: the call returns a job ID at once and the work goes on in the
 * background. These helpers look at that job through core.get_jobs.
 */
object Jobs {
    /**
     * Waits up to [graceMs] for the job to fail early (wrong arguments, the pool is busy, …) and
     * throws its error as [TnError.Call] if it does. A job still running after that counts as
     * started: long jobs like a scrub keep running and report progress elsewhere.
     */
    suspend fun awaitStart(c: Connection, result: JsonElement, graceMs: Long = 4_000) {
        val id = result.long() ?: return
        val until = System.currentTimeMillis() + graceMs
        while (System.currentTimeMillis() < until) {
            delay(700)
            val job = find(c, id) ?: return
            when (job["state"].str()) {
                "SUCCESS" -> return
                "FAILED", "ABORTED" -> throw TnError.Call(job["state"].str(), errorOf(job))
            }
        }
    }

    /**
     * Waits up to [timeoutMs] for the job to finish and returns its result; throws its error as
     * [TnError.Call] when it fails, and [TnError.Unreachable] when it doesn't finish in time.
     */
    suspend fun awaitResult(c: Connection, result: JsonElement, timeoutMs: Long = 120_000): JsonElement? {
        val id = result.long() ?: return result
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            delay(800)
            val job = find(c, id) ?: continue
            when (job["state"].str()) {
                "SUCCESS" -> return job["result"]
                "FAILED", "ABORTED" -> throw TnError.Call(job["state"].str(), errorOf(job))
            }
        }
        throw TnError.Unreachable(null)
    }

    private suspend fun find(c: Connection, id: Long): JsonElement? = runCatching {
        val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("id"), JsonPrimitive("="), JsonPrimitive(id)))))
        c.call("core.get_jobs", listOf(filter)).arr()?.firstOrNull()
    }.getOrNull()

    /** The job's own error text, first line only (the rest is a traceback). */
    private fun errorOf(job: JsonElement): String? =
        job["error"].str()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            ?: job["exception"].str()?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim()
}
