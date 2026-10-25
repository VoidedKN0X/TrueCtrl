package com.vknxworks.truectrl.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MAX_IMAGE_BYTES = 6L * 1024 * 1024

/**
 * Downloads one https image and decodes it (PNG, JPEG, WebP), at most 6 MiB and scaled down to
 * about [maxSide] pixels. Null when it can't be loaded. Only used after the user asked for images.
 */
suspend fun loadRemoteBitmap(http: OkHttpClient, url: String, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
    if (!url.startsWith("https://", ignoreCase = true)) return@withContext null
    runCatching {
        val client = http.newBuilder().followRedirects(true).followSslRedirects(false).build()
        val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null || body.contentLength() > MAX_IMAGE_BYTES) null else readCapped(body.byteStream())
        } ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** Reads at most [MAX_IMAGE_BYTES]; null when there's more. */
private fun readCapped(input: java.io.InputStream): ByteArray? = input.use { stream ->
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = stream.read(buf)
        if (n < 0) break
        total += n
        if (total > MAX_IMAGE_BYTES) return@use null
        out.write(buf, 0, n)
    }
    out.toByteArray()
}

/** An image from [url], with a spinner while loading and [fallback] when it can't be shown. */
@Composable
fun RemoteImage(
    http: OkHttpClient,
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit = {},
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val b = loadRemoteBitmap(http, url)
        if (b == null) failed = true else bitmap = b
    }
    val b = bitmap
    when {
        b != null -> Image(b.asImageBitmap(), contentDescription, modifier, contentScale = contentScale)
        failed -> Box(modifier, contentAlignment = Alignment.Center) { fallback() }
        else -> Box(modifier.background(CtrlColors.Raised), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}
