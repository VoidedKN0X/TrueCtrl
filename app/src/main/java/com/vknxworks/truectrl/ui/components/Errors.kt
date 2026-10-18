package com.vknxworks.truectrl.ui.components

import android.content.Context
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnVersion

/** A message for [e] the user can act on, from strings.xml. [host] names the server in the message. */
fun errorMessage(context: Context, e: Throwable, host: String): String {
    val base = when (e) {
        is TnError.Unreachable -> context.getString(R.string.error_unreachable, host)
        is TnError.UntrustedCertificate -> context.getString(R.string.error_untrusted_cert, host)
        is TnError.PinMismatch -> context.getString(R.string.error_pin_mismatch, host)
        is TnError.NotTrueNas -> context.getString(R.string.error_not_truenas, host)
        is TnError.RestOnly -> context.getString(R.string.error_rest_only, host, TnVersion.MINIMUM.label)
        is TnError.Unsupported -> context.getString(R.string.error_unsupported, e.version, TnVersion.MINIMUM.label)
        is TnError.BadApiKey -> context.getString(R.string.error_bad_api_key)
        is TnError.KeyExpired -> context.getString(R.string.error_key_expired)
        is TnError.BadPassword -> context.getString(R.string.error_bad_password)
        is TnError.BadOtp -> context.getString(R.string.error_bad_otp)
        is TnError.Denied -> context.getString(R.string.error_denied)
        is TnError.NeedsSignIn -> context.getString(R.string.error_session_ended, host)
        is TnError.NotAuthenticated -> context.getString(R.string.error_session_ended, host)
        is TnError.Forbidden -> context.getString(R.string.error_forbidden)
        is TnError.MethodMissing -> context.getString(R.string.error_method_missing)
        is TnError.Call -> context.getString(R.string.error_server)
        else -> context.getString(R.string.error_generic)
    }
    // TrueNAS's own wording helps for permission and server errors; network errors' detail is noise.
    val detail = when (e) {
        is TnError.Forbidden, is TnError.Call, is TnError.Denied -> e.detail
        is TnError.MethodMissing -> e.method
        else -> null
    }
    return if (detail.isNullOrBlank()) base else context.getString(R.string.error_with_detail, base, detail)
}
