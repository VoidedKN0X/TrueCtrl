package com.vknxworks.truectrl.data.api

import com.vknxworks.truectrl.data.server.Tls
import okhttp3.OkHttpClient
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/**
 * OkHttp client for one server: normal certificate checks, or only the pinned certificate when the
 * user confirmed a self-signed one (never "trust all"). Used for both HTTPS and the WebSocket.
 */
fun httpFor(base: OkHttpClient, host: String, certPin: String): OkHttpClient {
    if (certPin.isBlank()) return base
    val tm = Tls.PinnedTrustManager(certPin, Tls.systemTrustManager())
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
    return base.newBuilder()
        .sslSocketFactory(ctx.socketFactory, tm)
        .hostnameVerifier(Tls.pinnedHostnameVerifier(certPin, host))
        .build()
}
