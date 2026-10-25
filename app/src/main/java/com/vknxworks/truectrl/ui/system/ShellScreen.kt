package com.vknxworks.truectrl.ui.system

import com.vknxworks.truectrl.ui.components.topDivider
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import com.vknxworks.truectrl.ui.components.CtrlButton
import com.vknxworks.truectrl.ui.components.CtrlOutlinedButton
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.ShellSession
import com.vknxworks.truectrl.ui.components.SecureWindow
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

private const val ASSET_HOST = "https://appassets.androidplatform.net"

private enum class ShellState { CONNECTING, CONNECTED, CLOSED, FAILED }

/** What the hidden keyboard field always holds, so Backspace always has something to delete. */
private const val KEYBOARD_SENTINEL = "                                "

private val TERMINAL_KEYS = mapOf(
    "Escape" to "\u001b", "Tab" to "\t",
    "Up" to "\u001b[A", "Down" to "\u001b[B", "Right" to "\u001b[C", "Left" to "\u001b[D",
    "Home" to "\u001b[H", "End" to "\u001b[F", "PageUp" to "\u001b[5~", "PageDown" to "\u001b[6~", "Delete" to "\u001b[3~",
)

/** What the terminal page may call. Every method only acts on this screen's own session. */
private class TrueBridge(
    private val main: Handler,
    private val session: () -> ShellSession?,
    private val onPageReady: () -> Unit,
    private val onConnected: () -> Unit,
    private val onModsReleased: () -> Unit,
    private val onResize: (Int, Int) -> Unit,
) {
    @JavascriptInterface fun pageReady() { main.post { onPageReady() } }
    @JavascriptInterface fun sendText(text: String) { session()?.send(text.toByteArray()) }
    @JavascriptInterface fun sendBinary(b64: String) { session()?.send(Base64.decode(b64, Base64.NO_WRAP)) }
    @JavascriptInterface fun close() { session()?.close() }
    @JavascriptInterface fun connected() { main.post { onConnected() } }
    @JavascriptInterface fun modsReleased() { main.post { onModsReleased() } }
    @JavascriptInterface fun resize(cols: Int, rows: Int) { main.post { onResize(cols, rows) } }
    @JavascriptInterface fun log(message: String) = Unit
}

/**
 * The TrueNAS shell, full screen (xterm.js bundled with the app; the connection is made by
 * [ShellSession]). Needs the web shell privilege on the TrueNAS user and is never offered in
 * read-only mode.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShellScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val main = remember { Handler(Looper.getMainLooper()) }

    var state by remember { mutableStateOf(ShellState.CONNECTING) }
    var error by remember { mutableStateOf<String?>(null) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var fontSize by remember { mutableIntStateOf(13) }
    var landscape by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var input by remember { mutableStateOf(TextFieldValue(KEYBOARD_SENTINEL, TextRange(KEYBOARD_SENTINEL.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val sessionRef = remember { AtomicReference<ShellSession?>(null) }
    var bounds by remember { mutableStateOf<List<Int>?>(null) }

    fun js(code: String) {
        main.post { webView?.evaluateJavascript(code, null) }
    }

    fun setError(message: String?) {
        error = message
    }

    // A shell shows whatever the server prints (keys, passwords, configs): never in screenshots or Recents.
    SecureWindow()

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            sessionRef.getAndSet(null)?.close()
        }
    }

    // Read-only mode switched on while the shell is open: close it.
    LaunchedEffect(server?.readOnly) {
        if (server?.readOnly == true) {
            sessionRef.getAndSet(null)?.close()
            state = ShellState.FAILED
            error = context.getString(R.string.error_read_only)
        }
    }

    fun listenerFor(owner: ShellSession) = object : ShellSession.Listener {
        private fun current() = sessionRef.get() === owner
        override fun onConnected() { if (current()) js("pcOpen()") }
        override fun onBytes(bytes: ByteArray) {
            if (current()) js("pcRecv('${Base64.encodeToString(bytes, Base64.NO_WRAP)}')")
        }
        override fun onClosed(code: Int, reason: String) {
            if (!current()) return
            js("pcClosed($code, ${JSONObject.quote(reason)})")
            main.post { if (state != ShellState.FAILED) state = ShellState.CLOSED }
        }
        override fun onFailure(error: Throwable) {
            if (!current()) return
            js("pcError('socket')")
            val message = errorMessage(context, error, server?.host.orEmpty())
            main.post {
                state = ShellState.FAILED
                setError(message)
            }
        }
    }

    fun connect() {
        val connection = active?.connection ?: return
        val s = ShellSession(connection)
        sessionRef.getAndSet(s)?.close()
        state = ShellState.CONNECTING
        error = null
        scope.launch {
            try {
                s.connect(listenerFor(s))
            } catch (e: Exception) {
                if (sessionRef.get() !== s) return@launch
                state = ShellState.FAILED
                error = errorMessage(context, e, server?.host.orEmpty())
            }
        }
    }

    val bridge = remember {
        TrueBridge(
            main = main,
            session = { sessionRef.get() },
            onPageReady = { connect() },
            onConnected = { state = ShellState.CONNECTED },
            onModsReleased = { ctrl = false; alt = false },
            onResize = { cols, rows -> scope.launch { sessionRef.get()?.resize(cols, rows) } },
        )
    }

    fun reconnect() {
        state = ShellState.CONNECTING
        webView?.reload()
    }

    fun showKeyboard() {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(state) { if (state == ShellState.CONNECTED) showKeyboard() }

    /** The phone keyboard types into a hidden field; what it adds is sent, what it removes is Backspace. */
    fun onInput(value: TextFieldValue) {
        val old = KEYBOARD_SENTINEL
        val new = value.text
        if (value.composition != null && new.startsWith(old)) {
            input = value
            return
        }
        val common = old.commonPrefixWith(new).length
        val removed = old.length - common
        val added = new.substring(common)
        if (removed > 0) js("pcBackspace($removed)")
        if (added.isNotEmpty()) js("pcInput(${JSONObject.quote(added)})")
        input = TextFieldValue(KEYBOARD_SENTINEL, TextRange(KEYBOARD_SENTINEL.length))
    }

    fun onHardwareKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val seq = when (event.key) {
            Key.Enter, Key.NumPadEnter -> "\r"
            Key.Backspace -> "\u007f"
            Key.Escape -> TERMINAL_KEYS["Escape"]
            Key.Tab -> TERMINAL_KEYS["Tab"]
            Key.DirectionUp -> TERMINAL_KEYS["Up"]
            Key.DirectionDown -> TERMINAL_KEYS["Down"]
            Key.DirectionLeft -> TERMINAL_KEYS["Left"]
            Key.DirectionRight -> TERMINAL_KEYS["Right"]
            Key.MoveHome -> TERMINAL_KEYS["Home"]
            Key.MoveEnd -> TERMINAL_KEYS["End"]
            Key.PageUp -> TERMINAL_KEYS["PageUp"]
            Key.PageDown -> TERMINAL_KEYS["PageDown"]
            Key.Delete -> TERMINAL_KEYS["Delete"]
            else -> null
        } ?: return false
        js("pcKey(${JSONObject.quote(seq)})")
        return true
    }

    // The page runs in a WebView laid over the app's window rather than inside Compose: drawn inside
    // Compose, terminal pages stopped redrawing on some phones (the same as in ProxCtrl).
    val host = remember(activity) { activity?.findViewById<ViewGroup>(android.R.id.content) }
    DisposableEffect(host) {
        val wv = host?.let { h ->
            val ctx = h.context
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()
            WebView(ctx).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.setSupportZoom(false)
                webViewClient = object : WebViewClient() {
                    // Only the bundled files load; anything else gets an empty 403.
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)
                            ?: WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                addJavascriptInterface(bridge, "TrueBridge")
                isFocusable = false
                isFocusableInTouchMode = false
                val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                var downX = 0f
                var downY = 0f
                setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                        android.view.MotionEvent.ACTION_UP ->
                            if (kotlin.math.abs(e.x - downX) < slop && kotlin.math.abs(e.y - downY) < slop) main.post { showKeyboard() }
                    }
                    false
                }
                loadUrl("$ASSET_HOST/assets/console/terminal.html?font=$fontSize")
                webView = this
            }
        }
        if (host != null && wv != null) host.addView(wv, FrameLayout.LayoutParams(0, 0))
        onDispose {
            if (wv != null) {
                host?.removeView(wv)
                wv.destroy()
            }
            webView = null
        }
    }

    LaunchedEffect(bounds, webView) {
        val wv = webView ?: return@LaunchedEffect
        val h = host ?: return@LaunchedEffect
        val (x, y, w, hgt) = bounds ?: return@LaunchedEffect
        val origin = IntArray(2).also(h::getLocationInWindow)
        wv.translationX = (x - origin[0]).toFloat()
        wv.translationY = (y - origin[1]).toFloat()
        val lp = wv.layoutParams
        if (lp.width != w || lp.height != hgt) {
            lp.width = w
            lp.height = hgt
            wv.layoutParams = lp
        }
    }

    LaunchedEffect(state, webView) {
        webView?.visibility = if (state == ShellState.CONNECTED) View.VISIBLE else View.INVISIBLE
    }

    val white = androidx.compose.ui.graphics.Color.White
    Column(Modifier.fillMaxSize().background(CtrlColors.Background).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = white) }
            Text(
                stringResource(R.string.shell_title_on, server?.name.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = white,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(8); js("pcFontSize($fontSize)") }) {
                Icon(Icons.Outlined.TextDecrease, stringResource(R.string.console_font_smaller), tint = white)
            }
            IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28); js("pcFontSize($fontSize)") }) {
                Icon(Icons.Outlined.TextIncrease, stringResource(R.string.console_font_bigger), tint = white)
            }
            IconButton(onClick = {
                landscape = !landscape
                activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }) { Icon(Icons.Outlined.ScreenRotation, stringResource(R.string.console_rotate), tint = white) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.fillMaxSize().onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    bounds = listOf(pos.x.toInt(), pos.y.toInt(), coords.size.width, coords.size.height)
                },
            )
            BasicTextField(
                value = input,
                onValueChange = ::onInput,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.None,
                ),
                singleLine = false,
                textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
                cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focus)
                    .onPreviewKeyEvent(::onHardwareKey),
            )
            when (state) {
                ShellState.CONNECTING -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ShellState.CLOSED, ShellState.FAILED -> Surface(
                    shape = CtrlShapes.Card,
                    color = CtrlColors.Surface,
                    border = CtrlColors.CardStroke,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: stringResource(R.string.console_closed), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.padding(top = 12.dp))
                        CtrlButton(onClick = ::reconnect) { Text(stringResource(R.string.console_reconnect)) }
                    }
                }
                ShellState.CONNECTED -> Unit
            }
        }
        ExtraKeys(
            ctrl = ctrl,
            alt = alt,
            keyboardShown = imeVisible,
            onCtrl = { ctrl = !ctrl; js("pcSetMods($ctrl, $alt)") },
            onAlt = { alt = !alt; js("pcSetMods($ctrl, $alt)") },
            onKey = { seq -> js("pcKey(${JSONObject.quote(seq)})") },
            onKeyboard = { if (imeVisible) keyboard?.hide() else showKeyboard() },
            onPaste = {
                val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.paste_empty), Toast.LENGTH_SHORT).show()
                } else {
                    js("pcPaste(${JSONObject.quote(text)})")
                }
            },
        )
    }
}

/** Keys a phone keyboard doesn't have. Ctrl and Alt stay pressed until the next key. */
@Composable
private fun ExtraKeys(
    ctrl: Boolean,
    alt: Boolean,
    keyboardShown: Boolean,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    onKeyboard: () -> Unit,
    onPaste: () -> Unit,
) {
    Surface(color = CtrlColors.Surface, modifier = Modifier.fillMaxWidth().topDivider(CtrlColors.ControlBorder)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onKeyboard) {
                Icon(Icons.Outlined.Keyboard, stringResource(R.string.console_keyboard), tint = if (keyboardShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            KeyButton(stringResource(R.string.key_esc)) { onKey("\u001b") }
            KeyButton(stringResource(R.string.key_tab)) { onKey("\t") }
            FilterChip(selected = ctrl, onClick = onCtrl, label = { Text(stringResource(R.string.key_ctrl)) })
            FilterChip(selected = alt, onClick = onAlt, label = { Text(stringResource(R.string.key_alt)) })
            listOf("←" to "\u001b[D", "↑" to "\u001b[A", "↓" to "\u001b[B", "→" to "\u001b[C").forEach { (l, s) -> KeyButton(l) { onKey(s) } }
            listOf("/", "-", "|", "~").forEach { ch -> KeyButton(ch) { onKey(ch) } }
            listOf(
                R.string.key_home to "\u001b[H",
                R.string.key_end to "\u001b[F",
                R.string.key_pgup to "\u001b[5~",
                R.string.key_pgdn to "\u001b[6~",
                R.string.key_del to "\u001b[3~",
            ).forEach { (l, s) -> KeyButton(stringResource(l)) { onKey(s) } }
            IconButton(onClick = onPaste) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.console_paste)) }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    CtrlOutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}
