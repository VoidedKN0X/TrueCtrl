package com.vknxworks.truectrl.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The answer to a sign-in: done, or TrueNAS wants a one-time password next. */
sealed interface LoginResult {
    data object Success : LoginResult
    data object OtpRequired : LoginResult
}

/**
 * Finding and signing in to a TrueNAS server: the version check (no login needed), auth.login_ex
 * with an API key or a password (plus a one-time password), and what the user may do afterwards.
 * Only the JSON-RPC WebSocket API is used; the REST API is never called with credentials.
 */
object TnAuth {

    /**
     * GET /api/versions lists the API versions the server offers ("v25.04.2", "v25.10.5", …) and
     * needs no login. It shows the address is a TrueNAS with the JSON-RPC API, and whether its
     * certificate is trusted, before anything secret is sent.
     */
    suspend fun probe(http: OkHttpClient, baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val url = httpsBase(baseUrl).newBuilder().encodedPath("/api/versions").build()
        val request = Request.Builder().url(url).get().build()
        try {
            http.newCall(request).execute().use { response ->
                // The list is a few hundred bytes; never read more than a small, fixed amount of
                // whatever the address answers (a proxy page, a large download).
                val text = response.peekBody(MAX_PROBE_BYTES).string()
                val code = response.code
                when {
                    response.isSuccessful -> {
                        val list = runCatching { TnJson.parseToJsonElement(text).arr() }.getOrNull()
                            ?: throw TnError.NotTrueNas("HTTP $code, ${describe(response.header("Content-Type"), text)}")
                        list.mapNotNull { it.str() }
                    }
                    // TrueNAS 24.10 and older have no /api route: their web server redirects unknown
                    // paths to the web UI (/ui/). Redirects are never followed.
                    code in 300..399 -> {
                        val location = response.header("Location").orEmpty()
                        if (location.contains("/ui")) throw TnError.RestOnly()
                        throw TnError.NotTrueNas("HTTP $code → ${location.ifBlank { "?" }}")
                    }
                    // A reverse proxy answers 502/503/504 while TrueNAS itself is down or rebooting.
                    code >= 500 -> throw TnError.Unreachable(IOException("HTTP $code"))
                    // No /api/versions: an older TrueNAS that only has the REST API, or not TrueNAS at all.
                    // TrueCtrl never calls the REST API, so it doesn't probe further.
                    code == 404 -> throw TnError.RestOnly()
                    else -> throw TnError.NotTrueNas("HTTP $code")
                }
            }
        } catch (e: IOException) {
            throw TnError.fromIo(e)
        }
    }

    /** A short, safe description of an unexpected answer, shown in the error to help find the cause. */
    private fun describe(contentType: String?, text: String): String {
        val type = contentType?.substringBefore(';')?.trim().orEmpty().ifBlank { "no content type" }
        val start = text.trim().take(40).replace(Regex("\\s+"), " ")
        return if (start.isEmpty()) "$type, empty" else "$type: $start…"
    }

    /** The newest API version the server offers; null when none can be read. */
    fun newest(versions: List<String>): TnVersion? = versions.mapNotNull { TnVersion.parse(it) }.maxOrNull()

    /** Throws [TnError.Unsupported] when the server is older than TrueCtrl supports. */
    fun requireSupported(versions: List<String>): TnVersion {
        val newest = newest(versions) ?: throw TnError.NotTrueNas("versions: ${versions.joinToString().ifBlank { "none" }}")
        if (newest < TnVersion.MINIMUM) throw TnError.Unsupported(newest.label)
        return newest
    }

    /**
     * The WebSocket address for [baseUrl]: always the server's current API version, and always
     * wss://. TrueNAS revokes an API key that arrives over plain HTTP, so there is no ws:// fallback:
     * a base address that isn't https:// is refused with [TnError.InsecureAddress].
     */
    fun socketUrl(baseUrl: String): String {
        val https = httpsBase(baseUrl).newBuilder().encodedPath("/api/current").build().toString()
        return "wss://" + https.removePrefix("https://")
    }

    /** [baseUrl] parsed, only when it's https:// (the scheme ServerAddress always saves). */
    private fun httpsBase(baseUrl: String): HttpUrl =
        baseUrl.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: throw TnError.InsecureAddress()

    private const val MAX_PROBE_BYTES = 64L * 1024

    suspend fun loginApiKey(socket: RpcSocket, username: String, apiKey: String): LoginResult =
        interpret(
            socket.call(
                "auth.login_ex",
                listOf(buildJsonObject {
                    put("mechanism", "API_KEY_PLAIN")
                    put("username", username)
                    put("api_key", apiKey)
                }),
            ),
            apiKey = true,
        )

    suspend fun loginPassword(socket: RpcSocket, username: String, password: String): LoginResult =
        interpret(
            socket.call(
                "auth.login_ex",
                listOf(buildJsonObject {
                    put("mechanism", "PASSWORD_PLAIN")
                    put("username", username)
                    put("password", password)
                }),
            ),
            apiKey = false,
        )

    /** Answers OTP_REQUIRED with the 6-digit code from the user's authenticator app. */
    suspend fun continueOtp(socket: RpcSocket, code: String): LoginResult {
        val result = socket.call(
            "auth.login_ex_continue",
            listOf(buildJsonObject {
                put("mechanism", "OTP_TOKEN")
                put("otp_token", code.filter { it.isDigit() })
            }),
        )
        return try {
            interpret(result, apiKey = false)
        } catch (e: TnError.BadPassword) {
            throw TnError.BadOtp()
        }
    }

    private fun interpret(result: JsonElement, apiKey: Boolean): LoginResult =
        when (result["response_type"].str()) {
            "SUCCESS" -> LoginResult.Success
            "OTP_REQUIRED" -> LoginResult.OtpRequired
            "AUTH_ERR" -> throw if (apiKey) TnError.BadApiKey(null) else TnError.BadPassword()
            "EXPIRED" -> throw if (apiKey) TnError.KeyExpired() else TnError.Denied(null)
            "DENIED" -> throw TnError.Denied(null)
            // HA systems may redirect to the other controller; TrueCtrl doesn't follow redirects.
            "REDIRECT" -> throw TnError.Denied(result["urls"].arr()?.mapNotNull { it.str() }?.joinToString())
            else -> throw TnError.Call(null, result["response_type"].str())
        }

    /**
     * After signing in: the user's roles (auth.me), the methods it may call in the namespaces the
     * app uses (core.get_methods), and the exact version (system.version).
     */
    suspend fun capabilities(socket: RpcSocket, apiVersion: TnVersion?): Capabilities = coroutineScope {
        val me = async { runCatching { socket.call("auth.me") }.getOrNull() }
        val version = async { runCatching { socket.call("system.version") }.getOrNull() }
        val methods = Capabilities.NAMESPACES.map { ns ->
            async {
                runCatching { socket.call("core.get_methods", listOf(JsonPrimitive(ns))) }.getOrNull().obj()?.keys ?: emptySet()
            }
        }.awaitAll().flatten().toSet()
        val privilege = me.await()["privilege"]
        Capabilities(
            version = TnVersion.parse(version.await().str()) ?: apiVersion,
            methods = methods,
            roles = privilege["roles"].arr()?.mapNotNull { it.str() }?.toSet() ?: emptySet(),
            webShell = privilege["web_shell"].bool() ?: false,
        )
    }

    /** system.info, or null when this user can't read it. */
    suspend fun systemInfo(socket: RpcSocket): SystemInfo? =
        runCatching { SystemInfo.from(socket.call("system.info")) }.getOrNull()
}
