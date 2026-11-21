package com.vknxworks.truectrl.data.api

import com.vknxworks.truectrl.data.overview.SystemInfo
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
    suspend fun probe(http: OkHttpClient, baseUrl: String, allowHttp: Boolean): List<String> = withContext(Dispatchers.IO) {
        val base = checkedBase(baseUrl, allowHttp)
        val url = base.newBuilder().encodedPath("/api/versions").build()
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
                        // TrueNAS with "Web Interface HTTP -> HTTPS Redirect" on answers every
                        // plain HTTP request with a redirect to https://. Not followed: the user
                        // switches the address to https:// themselves.
                        if (!base.isHttps && location.startsWith("https://", ignoreCase = true)) throw TnError.RedirectsToHttps()
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
     * The WebSocket address for [baseUrl]: always the server's current API version. wss:// for an
     * https:// address; ws:// only for an http:// address the user accepted ([allowHttp]), never as
     * a fallback. An http:// address that wasn't accepted is refused with [TnError.InsecureAddress].
     */
    fun socketUrl(baseUrl: String, allowHttp: Boolean): String {
        val base = checkedBase(baseUrl, allowHttp)
        val url = base.newBuilder().encodedPath("/api/current").build().toString()
        return if (base.isHttps) "wss://" + url.removePrefix("https://") else "ws://" + url.removePrefix("http://")
    }

    /** [baseUrl] parsed: https://, or http:// only when the user accepted plain HTTP for it. */
    private fun checkedBase(baseUrl: String, allowHttp: Boolean): HttpUrl {
        val url = baseUrl.toHttpUrlOrNull() ?: throw TnError.InsecureAddress()
        if (!url.isHttps && !allowHttp) throw TnError.InsecureAddress()
        return url
    }

    private const val MAX_PROBE_BYTES = 64L * 1024

    /**
     * Signs in with an API key, only on an encrypted socket: TrueNAS revokes an API key used over
     * an insecure transport (auth.login_ex API_KEY_PLAIN, "Attempt to use over an insecure
     * transport"), so on ws:// the key is never sent and [TnError.ApiKeyOverHttp] is thrown.
     */
    suspend fun loginApiKey(socket: RpcSocket, username: String, apiKey: String): LoginResult {
        if (!socket.isSecure) throw TnError.ApiKeyOverHttp()
        return interpret(
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
    }

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
    /**
     * With a [cacheKey] (the saved server's id), the method list is remembered in memory for as long as
     * the app runs and reused on the next connect, as long as the server still reports exactly the same
     * version and this user the same roles; anything else reads the list again. core.get_methods returns
     * full method docs for some 40 namespaces, far too much to download every time the app comes back on screen.
     */
    suspend fun capabilities(socket: RpcSocket, apiVersion: TnVersion?, cacheKey: String? = null): Capabilities = coroutineScope {
        val me = async { runCatching { socket.call("auth.me") }.getOrNull() }
        val version = async { runCatching { socket.call("system.version") }.getOrNull() }
        val remembered = cacheKey?.let { methodCache[it] }
        // Nothing remembered for this server: ask for the methods at the same time, as before.
        val fresh = if (remembered == null) async { readMethods(socket) } else null
        val privilege = me.await()["privilege"]
        val versionText = version.await().str()
        val roles = privilege["roles"].arr()?.mapNotNull { it.str() }?.toSet() ?: emptySet()
        val webShell = privilege["web_shell"].bool() ?: false
        // Without a version or roles there is nothing to compare with: the list is read and not remembered.
        val fingerprint = if (versionText.isNullOrBlank() || roles.isEmpty()) null else versionText + "|" + roles.sorted().joinToString(",") + "|" + webShell
        val read: Pair<Set<String>, Boolean>? = when {
            fresh != null -> fresh.await()
            remembered != null && fingerprint != null && remembered.fingerprint == fingerprint -> null
            else -> readMethods(socket)
        }
        val methods = read?.first ?: remembered?.methods ?: emptySet()
        if (cacheKey != null && read != null) {
            // Only a complete list is remembered: one cut short by a dropped connection is read again next time.
            if (fingerprint != null && read.second && read.first.isNotEmpty()) {
                methodCache[cacheKey] = RememberedMethods(fingerprint, read.first)
            } else {
                methodCache.remove(cacheKey)
            }
        }
        Capabilities(
            version = TnVersion.parse(versionText) ?: apiVersion,
            methods = methods,
            roles = roles,
            webShell = webShell,
        )
    }

    private class RememberedMethods(val fingerprint: String, val methods: Set<String>)

    /** Method lists by saved server id. In memory only: gone when the app's process ends. */
    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, RememberedMethods>()

    /** Forgets the remembered method list of a server, so the next connect reads it again (Retry). */
    fun forgetMethods(cacheKey: String) {
        methodCache.remove(cacheKey)
    }

    /**
     * core.get_methods for every namespace the app uses. The second value is false when a namespace
     * couldn't be read because the connection dropped or timed out (the list is then incomplete); a
     * namespace this TrueNAS version doesn't have, or this user may not see, simply adds nothing.
     */
    private suspend fun readMethods(socket: RpcSocket): Pair<Set<String>, Boolean> = coroutineScope {
        val answers: List<Set<String>?> = Capabilities.NAMESPACES.map { ns ->
            async<Set<String>?> {
                try {
                    socket.call("core.get_methods", listOf(JsonPrimitive(ns))).obj()?.keys ?: emptySet<String>()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: TnError.Unreachable) {
                    null
                } catch (e: Exception) {
                    emptySet<String>()
                }
            }
        }.awaitAll()
        Pair(answers.filterNotNull().flatten().toSet(), answers.none { it == null })
    }

    /** system.info, or null when this user can't read it. */
    suspend fun systemInfo(socket: RpcSocket): SystemInfo? =
        runCatching { SystemInfo.from(socket.call("system.info")) }.getOrNull()
}
