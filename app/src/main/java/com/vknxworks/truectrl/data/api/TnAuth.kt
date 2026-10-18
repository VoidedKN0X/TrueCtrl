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
        val request = Request.Builder().url("$baseUrl/api/versions").get().build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val list = runCatching { TnJson.parseToJsonElement(text).arr() }.getOrNull()
                            ?: throw TnError.NotTrueNas(null)
                        list.mapNotNull { it.str() }
                    }
                    // A reverse proxy answers 502/503/504 while TrueNAS itself is down or rebooting.
                    response.code >= 500 -> throw TnError.Unreachable(IOException("HTTP ${response.code}"))
                    // No /api/versions: an older TrueNAS that only has the REST API, or not TrueNAS at all.
                    // TrueCtrl never calls the REST API, so it doesn't probe further.
                    response.code == 404 -> throw TnError.RestOnly()
                    else -> throw TnError.NotTrueNas("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            throw TnError.fromIo(e)
        }
    }

    /** The newest API version the server offers; null when none can be read. */
    fun newest(versions: List<String>): TnVersion? = versions.mapNotNull { TnVersion.parse(it) }.maxOrNull()

    /** Throws [TnError.Unsupported] when the server is older than TrueCtrl supports. */
    fun requireSupported(versions: List<String>): TnVersion {
        val newest = newest(versions) ?: throw TnError.NotTrueNas(null)
        if (newest < TnVersion.MINIMUM) throw TnError.Unsupported(newest.label)
        return newest
    }

    /** The WebSocket address for [baseUrl]: always the server's current API version. */
    fun socketUrl(baseUrl: String): String = baseUrl.replaceFirst("https://", "wss://", ignoreCase = true) + "/api/current"

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
