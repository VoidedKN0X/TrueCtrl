package com.vknxworks.truectrl.data.server

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How TrueCtrl signs in to a TrueNAS server. */
@Serializable
enum class AuthMethod { API_KEY, PASSWORD }

/**
 * A saved TrueNAS server. Secrets (API key, password) are never part of this object: they are
 * stored separately, encrypted with a Keystore key.
 */
@Serializable
data class Server(
    val id: String,
    /** What the user calls it; defaults to the TrueNAS hostname. */
    val name: String,
    /** Base address without a path, e.g. https://192.168.1.9. */
    val url: String,
    val auth: AuthMethod,
    /** The TrueNAS user the API key belongs to, or the user that signs in with a password. */
    val username: String = "",
    /** SHA-256 fingerprint of the pinned self-signed certificate; empty when the certificate is trusted normally. */
    val certPin: String = "",
    /** Hide every action that changes something on this server. */
    val readOnly: Boolean = false,
    /** The account needs a one-time password (2FA) to sign in, so the app can't reconnect on its own. */
    val usesOtp: Boolean = false,
    /** Last seen TrueNAS version, e.g. 25.10.1. */
    val tnVersion: String = "",
) {
    val host: String get() = url.toHttpUrlOrNull()?.host ?: url

    /** First letter shown in the profile picture. */
    val initial: String get() = name.ifBlank { host }.take(1)
}

/** Secrets for one server. Held in memory only while needed; persisted encrypted by [ServerStore]. */
data class ServerSecrets(
    val apiKey: String = "",
    /** Only kept for password sign-ins without a one-time password, so the app can reconnect. */
    val password: String = "",
)

object ServerAddress {
    /**
     * Turns what the user typed into a base URL. TrueNAS serves its web UI and API on the standard
     * HTTPS port, so no port means 443 ("192.168.1.9" → https://192.168.1.9). A typed port is kept.
     * Paths are dropped. Returns null for anything that isn't a usable https address.
     */
    fun normalize(input: String): HttpUrl? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return null
        if (text.startsWith("http://", ignoreCase = true)) return null
        val withScheme = if (text.startsWith("https://", ignoreCase = true)) text else "https://$text"
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        return HttpUrl.Builder().scheme("https").host(parsed.host).port(parsed.port).build()
    }

    /** The base URL as stored: no trailing slash. */
    fun base(url: HttpUrl): String = url.toString().trimEnd('/')

    fun isPlainHttp(input: String): Boolean = input.trim().startsWith("http://", ignoreCase = true)
}
