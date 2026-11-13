package com.vknxworks.truectrl.data.notify

import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.httpFor
import com.vknxworks.truectrl.data.overview.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Where disk-health warnings are also posted: a topic on the user's own ntfy server. */
data class NtfyTarget(
    /** The server's root address, normalised (no trailing slash). */
    val server: HttpUrl,
    val topic: String,
    /** An access token (tk_…), or empty for a topic anyone may publish to. */
    val token: String = "",
    /** SHA-256 of a self-signed certificate the user trusted for this server, or empty. */
    val certPin: String = "",
)

/** Why a target can't be used, for the settings screen. */
enum class NtfyProblem { NO_SERVER, BAD_SERVER, NO_TOPIC, BAD_TOPIC, TOKEN_OVER_HTTP }

/** The ntfy server answered with an error (401/403: the token or the topic's access rules). */
class NtfyHttpException(val code: Int) : IOException("HTTP $code")

/**
 * Posts messages to a topic on the user's ntfy server with ntfy's JSON publishing
 * (https://docs.ntfy.sh/publish/#publish-as-json): POST to the server's root address with the topic in
 * the body, so titles and texts in any language need no header encoding. Talks to that server only:
 * redirects are never followed (the shared client doesn't), a self-signed certificate works only after
 * the user trusted its fingerprint, and an access token is never sent over plain HTTP.
 */
object Ntfy {
    /** ntfy's own rule for topic names. */
    private val TOPIC = Regex("^[-_A-Za-z0-9]{1,64}$")
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Checks what the user typed; the target, or what's wrong with it. */
    fun target(server: String, topic: String, token: String, certPin: String): Pair<NtfyTarget?, NtfyProblem?> {
        val s = server.trim().trimEnd('/')
        if (s.isEmpty()) return null to NtfyProblem.NO_SERVER
        val withScheme = if (s.contains("://")) s else "https://$s"
        val url = withScheme.toHttpUrlOrNull()
            ?.takeIf { (it.scheme == "https" || it.scheme == "http") && it.host.isNotBlank() && it.query == null && it.fragment == null }
            ?: return null to NtfyProblem.BAD_SERVER
        val t = topic.trim().trimStart('/')
        if (t.isEmpty()) return null to NtfyProblem.NO_TOPIC
        if (!TOPIC.matches(t)) return null to NtfyProblem.BAD_TOPIC
        if (!url.isHttps && token.isNotBlank()) return null to NtfyProblem.TOKEN_OVER_HTTP
        return NtfyTarget(url, t, token.trim(), certPin) to null
    }

    /** ntfy priority (1–5) for a severity. */
    fun priority(s: Severity): Int = when (s) {
        Severity.CRITICAL -> 5
        Severity.WARNING -> 4
        Severity.INFO -> 3
    }

    /** The publish request for [title] and [message]. */
    fun request(target: NtfyTarget, title: String, message: String, severity: Severity): Request {
        val body = buildJsonObject {
            put("topic", target.topic)
            put("title", title.take(250))
            put("message", message.take(3_500))
            put("priority", priority(severity))
            put("tags", JsonArray(listOf(JsonPrimitive(if (severity == Severity.CRITICAL) "rotating_light" else "warning"), JsonPrimitive("floppy_disk"))))
        }
        val b = Request.Builder()
            // The JSON endpoint is the server's root, which includes a path when ntfy runs under one.
            .url(target.server.newBuilder().encodedPath(target.server.encodedPath.trimEnd('/') + "/").query(null).build())
            .post(body.toString().toRequestBody(JSON))
        if (target.token.isNotBlank()) b.header("Authorization", "Bearer ${target.token}")
        return b.build()
    }

    /** Posts the message; throws a [TnError] (certificate or network) or [NtfyHttpException] when it fails. */
    suspend fun publish(base: OkHttpClient, target: NtfyTarget, title: String, message: String, severity: Severity) =
        withContext(Dispatchers.IO) {
            val client = httpFor(base, target.server.host, target.certPin).newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            try {
                client.newCall(request(target, title, message, severity)).execute().use { r ->
                    // A redirect isn't followed, and doesn't count as delivered.
                    if (!r.isSuccessful) throw NtfyHttpException(r.code)
                }
            } catch (e: NtfyHttpException) {
                throw e
            } catch (e: IOException) {
                throw TnError.fromIo(e)
            }
        }
}
