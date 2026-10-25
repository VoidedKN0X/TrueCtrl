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
    /** Base address without a path, e.g. https://192.168.1.9 (or http://192.168.1.9 after the HTTP warning). */
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
    /** The Proxmox VM this TrueNAS runs in, to open it in ProxCtrl (start it again after a shutdown). */
    val proxmoxVmId: Int? = null,
    /**
     * The user accepted the "Use an unencrypted connection?" warning for this http:// address.
     * Without it, nothing is ever sent to a plain HTTP address.
     */
    val httpAccepted: Boolean = false,
) {
    val host: String get() = url.toHttpUrlOrNull()?.host ?: url

    /** The address is plain http:// (not encrypted). */
    val isPlainHttp: Boolean get() = ServerAddress.isPlainHttp(url)

    /** First letter shown in the profile picture. */
    val initial: String get() = name.ifBlank { host }.take(1)
}

/** Secrets for one server. Held in memory only while needed; persisted encrypted by [ServerStore]. */
data class ServerSecrets(
    val apiKey: String = "",
    /** Only kept for password sign-ins without a one-time password, so the app can reconnect. */
    val password: String = "",
) {
    /** Never the secrets themselves, so they can't end up in a log, an error or a crash report. */
    override fun toString(): String =
        "ServerSecrets(apiKey=${if (apiKey.isEmpty()) "none" else "***"}, password=${if (password.isEmpty()) "none" else "***"})"
}

object ServerAddress {
    /**
     * Turns what the user typed into a base URL. Without a scheme it's always HTTPS on the standard
     * port ("192.168.1.9" → https://192.168.1.9); never a silent fallback to HTTP. Only an address
     * the user typed with http:// is plain HTTP (port 80 unless one is typed). A typed port is kept.
     * Paths are dropped. Returns null for anything that isn't a usable http(s) address.
     */
    fun normalize(input: String): HttpUrl? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return null
        val withScheme = when {
            text.startsWith("https://", ignoreCase = true) || text.startsWith("http://", ignoreCase = true) -> text
            text.contains("://") -> return null
            else -> "https://$text"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        return HttpUrl.Builder().scheme(parsed.scheme).host(parsed.host).port(parsed.port).build()
    }

    /** The base URL as stored: no trailing slash. */
    fun base(url: HttpUrl): String = url.toString().trimEnd('/')

    fun isPlainHttp(input: String): Boolean = input.trim().startsWith("http://", ignoreCase = true)
}
