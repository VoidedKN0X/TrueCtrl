package com.vknxworks.truectrl.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import java.util.concurrent.TimeUnit

private const val MAX_IMAGE_BYTES = 6L * 1024 * 1024

/**
 * Downloads one https image and decodes it (PNG, JPEG, WebP), at most 6 MiB and scaled down to
 * at most [maxSide] pixels on each side. Null when it can't be loaded. Only used after the user asked for images.
 */
suspend fun loadRemoteBitmap(http: OkHttpClient, url: String, maxSide: Int = 1280): Bitmap? = withContext(Dispatchers.IO) {
    if (!url.startsWith("https://", ignoreCase = true)) return@withContext null
    runCatching {
        // Redirects stay on https. The catalog's image host isn't the user's TrueNAS: it isn't told
        // which app is asking (no TrueCtrl User-Agent), and a stalled download gives up after 30 s.
        val client = imageClient(http)
        val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null || body.contentLength() > MAX_IMAGE_BYTES) null else readCapped(body.byteStream())
        } ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        // Scaled down until both sides fit in maxSide, so a small file with huge dimensions can't
        // make a bitmap big enough to run the phone out of memory.
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

@Volatile
private var imageClients: Pair<OkHttpClient, OkHttpClient>? = null

/** The client for image downloads, made once from [http] and reused instead of being rebuilt for every image. */
private fun imageClient(http: OkHttpClient): OkHttpClient {
    imageClients?.let { (base, client) -> if (base === http) return client }
    val client = http.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false)
        .apply { interceptors().clear() }
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    imageClients = http to client
    return client
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

private const val IMAGE_CACHE_BYTES = 8 * 1024 * 1024

/**
 * Decoded images that were shown before, so coming back to a page doesn't download and decode them
 * again. In memory only (nothing is written to storage), at most 8 MiB of pixels, the least recently
 * used dropped first, and gone when the app's process ends. Keyed by address and decode size.
 */
private val imageCache = object : LruCache<String, Bitmap>(IMAGE_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** An image from [url], with a spinner while loading and [fallback] when it can't be shown. */
@Composable
fun RemoteImage(
    http: OkHttpClient,
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /** The largest side, in pixels, the image is decoded at. Small for icons, so they don't take the memory of a full picture. */
    maxSide: Int = 1280,
    fallback: @Composable () -> Unit = {},
) {
    val cacheKey = "$maxSide $url"
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(imageCache.get(cacheKey)) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val b = loadRemoteBitmap(http, url, maxSide)
        if (b == null) {
            failed = true
        } else {
            // Big pictures (full-size screenshots) are shown but not kept.
            if (b.byteCount <= IMAGE_CACHE_BYTES / 4) imageCache.put(cacheKey, b)
            bitmap = b
        }
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
