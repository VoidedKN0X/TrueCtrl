package com.vknxworks.truectrl.data.api

import com.vknxworks.truectrl.data.server.Tls
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

/**
 * Everything that can go wrong talking to TrueNAS, sorted into what the user can act on.
 * The UI turns these into messages from strings.xml; [detail] is TrueNAS's own wording when it gave one.
 */
sealed class TnError(val detail: String? = null, cause: Throwable? = null) : IOException(detail, cause) {
    /** No route, DNS failure, refused, timed out, or the connection dropped. */
    class Unreachable(cause: Throwable?) : TnError(cause?.message, cause)

    /** The certificate isn't trusted by the phone and hasn't been pinned. */
    class UntrustedCertificate(cause: Throwable?) : TnError(cause?.message, cause)

    /** The server now presents a different certificate from the pinned one. */
    class PinMismatch(cause: Throwable?) : TnError(cause?.message, cause)

    /** The address answered, but not like a TrueNAS server (e.g. a reverse proxy page). */
    class NotTrueNas(detail: String?) : TnError(detail)

    /** No JSON-RPC API: an older TrueNAS that only has the REST API (which TrueCtrl doesn't use), or not TrueNAS. */
    class RestOnly : TnError(null)

    /** A TrueNAS version older than TrueCtrl supports. */
    class Unsupported(val version: String) : TnError(version)

    /** TrueNAS rejected the API key (wrong, revoked, or for another user). */
    class BadApiKey(detail: String?) : TnError(detail)

    /** The API key has expired. */
    class KeyExpired : TnError(null)

    /** Wrong username or password. */
    class BadPassword : TnError(null)

    /** The one-time password wasn't accepted. */
    class BadOtp : TnError(null)

    /** The account exists but may not sign in (disabled, no web UI access, or locked out). */
    class Denied(detail: String?) : TnError(detail)

    /** A password session with a one-time password ended; the user has to sign in again. */
    class NeedsSignIn : TnError(null)

    /** The connection isn't signed in (any more); the app signs in again on its own. */
    class NotAuthenticated : TnError(null)

    /** Signed in, but this user lacks the role for this call. */
    class Forbidden(detail: String?) : TnError(detail)

    /** The method doesn't exist on this TrueNAS version. */
    class MethodMissing(val method: String) : TnError(method)

    /** Any other error from TrueNAS ([errname] like EINVAL or ENOENT). */
    class Call(val errname: String?, detail: String?) : TnError(detail)

    /** Read-only mode is on for this server and [method] could change something, so it was never sent. */
    class ReadOnly(val method: String) : TnError(method)

    /** The address is plain http:// and the user hasn't accepted the HTTP warning for it: nothing is sent. */
    class InsecureAddress : TnError(null)

    /**
     * An API key would be sent over plain HTTP. TrueNAS revokes an API key that arrives over an
     * unencrypted connection, so TrueCtrl never sends one there; the user has to sign in with a
     * password or use https://.
     */
    class ApiKeyOverHttp : TnError(null)

    /** The http:// address answered with a redirect to HTTPS (TrueNAS's HTTP → HTTPS redirect is on). Never followed. */
    class RedirectsToHttps : TnError(null)

    /** Errors that retrying on its own can't fix: the user has to change something first. */
    val needsUser: Boolean
        get() = this is UntrustedCertificate || this is PinMismatch || this is NotTrueNas || this is RestOnly ||
            this is Unsupported || this is BadApiKey || this is KeyExpired || this is BadPassword ||
            this is BadOtp || this is Denied || this is NeedsSignIn || this is InsecureAddress ||
            this is ApiKeyOverHttp || this is RedirectsToHttps

    companion object {
        /** Sorts a network-level failure from OkHttp. */
        fun fromIo(e: Throwable): TnError = when {
            e is TnError -> e
            Tls.isPinMismatch(e) -> PinMismatch(e)
            Tls.isCertificateError(e) -> UntrustedCertificate(e)
            e is SSLHandshakeException -> UntrustedCertificate(e)
            else -> Unreachable(e)
        }
    }
}
