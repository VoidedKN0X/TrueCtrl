package com.vknxworks.truectrl.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.Role
import com.vknxworks.truectrl.ui.components.CtrlLabel
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlType
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.lock.LockVault
import com.vknxworks.truectrl.ui.components.ConfirmDialog
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.PillButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import javax.crypto.Cipher
import kotlin.math.roundToInt

/**
 * App lock state: whether the app is unlocked, and when it locks again. The secrets live in [LockVault].
 * Locking wipes the vault key from memory and closes the connection, so the saved sign-in can't be
 * read until the next unlock (see ServerStore for the one exception, background checks).
 */
object AppLock {
    var unlocked by mutableStateOf(false)
        private set

    /** Goes up every time the app locks, so the lock screen offers the fingerprint once per lock. */
    var lockCount by mutableIntStateOf(0)
        private set

    /** A PIN screen shown on top of the app: turning the lock on or off, or changing the PIN. */
    var request by mutableStateOf<PinRequest?>(null)

    /** Set by the app: wipes the vault key and closes the connection. */
    var onLocked: () -> Unit = {}

    /**
     * Goes up every time you come back to the app after really leaving it (not for the fingerprint prompt
     * itself), so the lock screen offers the fingerprint again on every return, not just once per lock.
     */
    var returns by mutableIntStateOf(0)
        private set

    /** The fingerprint or screen lock prompt that is open right now, if any. */
    private var promptSignal: CancellationSignal? = null
    private var promptStartedAt = 0L
    private var promptGeneration = 0

    /**
     * A prompt is open. Some phones drop a prompt without ever answering (for example one asked for while
     * the app was still coming back on screen); such a prompt stops counting after a minute, and asking
     * again always replaces it, so it can never block fingerprint unlock until the app is closed.
     */
    private val authenticating: Boolean
        get() = promptSignal != null && SystemClock.elapsedRealtime() - promptStartedAt < PROMPT_STALE_MS

    private const val PROMPT_STALE_MS = 60_000L

    /**
     * Starts tracking a new prompt, replacing one that is still marked as open (its late answer, if any, is
     * ignored). The returned check is true once, and only for the newest prompt.
     */
    private fun openPrompt(): Pair<CancellationSignal, () -> Boolean> {
        promptSignal?.let { runCatching { it.cancel() } }
        val generation = ++promptGeneration
        val signal = CancellationSignal()
        promptSignal = signal
        promptStartedAt = SystemClock.elapsedRealtime()
        val finish: () -> Boolean = {
            if (generation != promptGeneration || promptSignal !== signal) {
                false
            } else {
                promptSignal = null
                true
            }
        }
        return signal to finish
    }

    private var backgroundAt = 0L
    private var expectReturnAt = 0L

    /**
     * The app itself is opening another app (browser, ProxCtrl, ntfy, Android settings). That trip
     * doesn't count as leaving, so you don't have to unlock again when you come back.
     */
    fun expectReturn() {
        expectReturnAt = SystemClock.elapsedRealtime()
    }

    /** The current trip away was started by the app itself (picker, share sheet, browser …). */
    private var exemptTrip = false

    fun onBackground(enabled: Boolean, timeoutMillis: Long) {
        if (authenticating) return
        backgroundAt = SystemClock.elapsedRealtime()
        // Only right after the app opened something itself; leaving later still counts.
        exemptTrip = expectReturnAt > 0 && backgroundAt - expectReturnAt < 5_000
        expectReturnAt = 0L
        // "As soon as you leave" locks right away, so the key is gone while the app is in the background.
        if (enabled && unlocked && timeoutMillis == 0L && !exemptTrip) lock()
    }

    fun onForeground(enabled: Boolean, timeoutMillis: Long) {
        val since = backgroundAt
        val exempt = exemptTrip
        backgroundAt = 0L
        expectReturnAt = 0L
        exemptTrip = false
        if (since > 0) returns++
        if (!enabled || !unlocked || since <= 0 || timeoutMillis < 0) return
        // A trip the app started still locks if you stay away long: after 5 minutes, or the chosen time if longer.
        val limit = if (exempt) maxOf(timeoutMillis, EXEMPT_TRIP_MAX) else timeoutMillis
        if (SystemClock.elapsedRealtime() - since >= limit) lock()
    }

    private const val EXEMPT_TRIP_MAX = 5 * 60_000L

    fun lock() {
        unlocked = false
        lockCount++
        request = null
        onLocked()
    }

    fun markUnlocked() {
        backgroundAt = 0L
        unlocked = true
    }

    /** Only class 3 ("strong") biometrics: camera-only face unlock can be fooled by a photo. */
    fun biometricAvailable(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /**
     * Shows the fingerprint prompt for [cipher], a Keystore key that only works after a strong match.
     * [onSuccess] gets the unlocked cipher; [onError] gets a message, or null when cancelled.
     */
    fun biometricPrompt(
        activity: Activity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onError: (String?) -> Unit = {},
        onSuccess: (Cipher) -> Unit,
    ) {
        // Replaces a prompt that is still marked as open; its late answer (if any) is ignored.
        val (signal, finish) = openPrompt()
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton(activity.getString(R.string.lock_use_pin), activity.mainExecutor) { _, _ ->
                    if (finish()) onError(null)
                }
                .build()
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                signal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!finish()) return
                        val c = result.cryptoObject?.cipher
                        if (c != null) onSuccess(c) else onError(activity.getString(R.string.lock_fingerprint_failed))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!finish()) return
                        val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        onError(if (cancelled) null else errString.toString())
                    }
                },
            )
        } catch (e: Exception) {
            if (finish()) onError(e.message ?: activity.getString(R.string.lock_fingerprint_failed))
        }
    }

    /** The phone's own screen lock is set up (used once, by people updating from the old app lock). */
    fun legacyAvailable(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(LEGACY_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    private const val LEGACY_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** The old app lock: confirms it's you once, before you choose a PIN. */
    fun legacyAuthenticate(activity: Activity, onResult: (String?) -> Unit) {
        // Replaces a prompt that is still marked as open; its late answer (if any) is ignored.
        val (signal, finish) = openPrompt()
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(activity.getString(R.string.lock_prompt_title))
                .setSubtitle(activity.getString(R.string.lock_legacy_prompt_subtitle))
                .setAllowedAuthenticators(LEGACY_AUTHENTICATORS)
                .build()
            prompt.authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!finish()) return
                    markUnlocked()
                    onResult(null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (finish()) onResult(errString.toString())
                }
            })
        } catch (e: Exception) {
            if (finish()) onResult(e.message ?: activity.getString(R.string.lock_fingerprint_failed))
        }
    }
}

/**
 * Waits until the app is really back on screen: resumed, with its window focused. A prompt asked for earlier
 * (while the app is still coming back from the background) is dropped by the system, on some phones without
 * any answer, which used to leave fingerprint unlock not working until the app was closed.
 */
suspend fun awaitOnScreen(lifecycle: Lifecycle, windowInfo: WindowInfo) {
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    snapshotFlow { windowInfo.isWindowFocused }.first { it }
}

/** What a PIN screen on top of the app is for. */
enum class PinRequest { ENABLE, CHANGE, DISABLE }

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Whether the app is covered by a lock or PIN screen right now. */
fun lockCovering(lockSetting: Boolean, vault: LockVault): Boolean =
    (lockSetting && !vault.enabled) || (vault.enabled && !AppLock.unlocked) || AppLock.request != null

/**
 * The lock and PIN screens shown on top of the app. The app underneath stays where it was, so
 * nothing you were typing is lost, but it can't be seen or touched.
 */
@Composable
fun AppLockOverlay(lockSetting: Boolean) {
    val c = LocalContext.current.container
    val vault = c.vault
    when {
        // Updated from the old app lock (the phone's screen lock): confirm once, then choose a PIN.
        lockSetting && !vault.enabled -> {
            if (!AppLock.unlocked) LegacyLockedScreen()
            else PinSetupScreen(
                title = stringResource(R.string.lock_choose_pin),
                intro = stringResource(R.string.lock_migrate_intro),
                verifyCurrent = false,
                cancellable = false,
                onDone = { pin -> c.enableLock(pin) },
                onCancel = {},
            )
        }
        vault.enabled && !AppLock.unlocked -> PinLockScreen()
        else -> when (AppLock.request) {
            PinRequest.ENABLE -> PinSetupScreen(
                title = stringResource(R.string.lock_choose_pin),
                intro = stringResource(R.string.lock_enable_intro, LockVault.PIN_MIN),
                verifyCurrent = false,
                cancellable = true,
                onDone = { pin -> c.enableLock(pin); AppLock.request = null },
                onCancel = { AppLock.request = null },
            )
            PinRequest.CHANGE -> PinSetupScreen(
                title = stringResource(R.string.lock_change_pin),
                intro = stringResource(R.string.lock_change_intro, LockVault.PIN_MIN),
                verifyCurrent = true,
                cancellable = true,
                onDone = { pin -> withContext(Dispatchers.Default) { vault.changePin(pin) }; AppLock.request = null },
                onCancel = { AppLock.request = null },
            )
            PinRequest.DISABLE -> PinSetupScreen(
                title = stringResource(R.string.lock_turn_off),
                intro = stringResource(R.string.lock_turn_off_intro),
                verifyCurrent = true,
                verifyOnly = true,
                cancellable = true,
                onDone = { c.disableLock(); AppLock.request = null },
                onCancel = { AppLock.request = null },
            )
            null -> Unit
        }
    }
}

/** Swallows every touch, so nothing underneath a lock screen can be used. */
@Composable
private fun Modifier.blockTouches(): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }

@Composable
private fun LegacyLockedScreen() {
    val context = LocalContext.current
    val activity = context.findActivity()
    var error by remember { mutableStateOf<String?>(null) }
    fun unlock() {
        val a = activity ?: return
        AppLock.legacyAuthenticate(a) { error = it }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(AppLock.lockCount, AppLock.returns) {
        // No screen lock on the phone any more: nothing to confirm with, and anyone holding the phone
        // could already open it, so go straight to choosing a PIN.
        if (!AppLock.legacyAvailable(context)) {
            AppLock.markUnlocked()
            return@LaunchedEffect
        }
        // Asked on every lock and every return, once the app is really on screen.
        awaitOnScreen(lifecycle, windowInfo)
        unlock()
    }
    BackHandler { activity?.moveTaskToBack(true) }
    Surface(Modifier.fillMaxSize().blockTouches(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LockBadge()
                CtrlLabel(stringResource(R.string.lock_brand))
                Text(stringResource(R.string.lock_title_short), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.lock_legacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                PillButton(stringResource(R.string.lock_unlock), onClick = ::unlock, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LockBadge() {
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Accent),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Lock, null, Modifier.size(30.dp), tint = CtrlColors.OnAccent) }
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?". */
@Composable
private fun PinLockScreen() {
    val context = LocalContext.current
    val c = context.container
    val vault = c.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }
    var forgetting by remember { mutableStateOf(false) }
    val bioAvailable = remember(AppLock.lockCount, AppLock.returns) {
        vault.biometricEnabled && AppLock.biometricAvailable(context) && !vault.pinRequired()
    }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    fun askFingerprint() {
        val a = activity ?: return
        val cipher = vault.biometricUnlockCipher() ?: run {
            message = if (!vault.biometricEnabled) context.getString(R.string.lock_fingerprint_changed) else null
            return
        }
        AppLock.biometricPrompt(
            a,
            context.getString(R.string.lock_prompt_title),
            context.getString(R.string.lock_prompt_subtitle),
            cipher,
            onError = { message = it },
        ) { unlocked ->
            if (vault.unlockWithBiometric(unlocked)) c.onUnlocked() else message = context.getString(R.string.lock_fingerprint_failed)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowInfo = LocalWindowInfo.current
    // Offered on every lock and every time you come back to the lock screen, once the app is really on screen.
    LaunchedEffect(AppLock.lockCount, AppLock.returns) {
        if (!bioAvailable) return@LaunchedEffect
        awaitOnScreen(lifecycle, windowInfo)
        askFingerprint()
    }

    fun submit() {
        if (checking || lockout > 0 || pin.length < LockVault.PIN_MIN) return
        val entered = pin
        checking = true
        scope.launch {
            // Slow on purpose (it makes guessing slow too), so off the main thread.
            val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
            checking = false
            pin = ""
            when (result) {
                LockVault.PinResult.Ok -> c.onUnlocked()
                LockVault.PinResult.Wrong, LockVault.PinResult.LockedOut -> {
                    lockout = vault.lockoutRemaining()
                    message = context.getString(R.string.lock_wrong_pin)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
                }
            }
        }
    }

    BackHandler { activity?.moveTaskToBack(true) }
    PinPadScreen(
        title = stringResource(R.string.lock_title_short),
        message = when {
            lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
            message != null -> message!!
            vault.biometricEnabled && vault.pinRequired() -> stringResource(R.string.lock_pin_required)
            else -> stringResource(R.string.lock_enter_pin)
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = checking,
        enabled = lockout == 0L && !checking,
        onDigit = { d ->
            if (pin.length < LockVault.PIN_MAX) {
                message = null
                pin += d
                if (pin.length == vault.pinLength) submit()
            }
        },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::submit,
        showConfirm = pin.length >= LockVault.PIN_MIN && pin.length != vault.pinLength,
        confirmLabel = stringResource(R.string.lock_ok),
        onFingerprint = if (bioAvailable) ::askFingerprint else null,
        bottom = {
            TextButton(onClick = { forgetting = true }, colors = ButtonDefaults.textButtonColors(contentColor = CtrlColors.Muted)) {
                Text(stringResource(R.string.lock_forgot))
            }
        },
    )
    if (forgetting) {
        ConfirmDialog(
            title = stringResource(R.string.lock_forgot_title),
            body = stringResource(R.string.lock_forgot_body),
            confirmLabel = stringResource(R.string.lock_forgot_confirm),
            destructive = true,
            onConfirm = {
                forgetting = false
                scope.launch { c.forgetEverything() }
            },
            onDismiss = { forgetting = false },
        )
    }
}

/** Choosing a PIN (twice), changing it (current, then new twice) or confirming it to turn the lock off. */
@Composable
private fun PinSetupScreen(
    title: String,
    intro: String,
    verifyCurrent: Boolean,
    cancellable: Boolean,
    onDone: suspend (String) -> Unit,
    onCancel: () -> Unit,
    verifyOnly: Boolean = false,
) {
    val context = LocalContext.current
    val vault = context.container.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    // 0 = current PIN, 1 = new PIN, 2 = repeat new PIN
    var step by remember { mutableIntStateOf(if (verifyCurrent) 0 else 1) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    BackHandler { if (cancellable) onCancel() else activity?.moveTaskToBack(true) }

    fun wrong(text: String) {
        message = text
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45)) }
    }

    fun finish(newPin: String) {
        busy = true
        scope.launch {
            runCatching { onDone(newPin) }.onFailure { busy = false; wrong(context.getString(R.string.lock_save_failed)) }
        }
    }

    fun next() {
        if (busy || lockout > 0) return
        when (step) {
            0 -> {
                if (pin.length < LockVault.PIN_MIN) return
                val entered = pin
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
                    busy = false
                    if (result == LockVault.PinResult.Ok) {
                        pin = ""
                        message = null
                        if (verifyOnly) finish(entered) else step = 1
                    } else {
                        lockout = vault.lockoutRemaining()
                        wrong(context.getString(R.string.lock_wrong_pin))
                    }
                }
            }
            1 -> {
                if (pin.length < LockVault.PIN_MIN) return
                if (pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)) {
                    wrong(context.getString(R.string.lock_pin_too_easy))
                    return
                }
                first = pin
                pin = ""
                message = null
                step = 2
            }
            else -> {
                if (pin != first) {
                    first = ""
                    step = 1
                    wrong(context.getString(R.string.lock_pin_mismatch))
                    return
                }
                finish(pin)
            }
        }
    }

    PinPadScreen(
        title = title,
        message = when {
            lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
            message != null -> message!!
            step == 0 -> stringResource(R.string.lock_enter_current_pin)
            step == 1 -> intro
            else -> stringResource(R.string.lock_repeat_pin)
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = busy,
        enabled = !busy && lockout == 0L,
        onDigit = { d -> if (pin.length < LockVault.PIN_MAX) { message = null; pin += d } },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::next,
        showConfirm = pin.length >= LockVault.PIN_MIN,
        confirmLabel = stringResource(if (step == 2 || (step == 0 && verifyOnly)) R.string.lock_done else R.string.lock_next),
        bottom = {
            if (cancellable) {
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = CtrlColors.Muted)) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun PinPadScreen(
    title: String,
    message: String,
    isError: Boolean,
    pin: String,
    shake: Float,
    checking: Boolean,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    showConfirm: Boolean,
    confirmLabel: String,
    bottom: @Composable () -> Unit,
    onFingerprint: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Surface(Modifier.fillMaxSize().blockTouches(), color = CtrlColors.Background) {
        // Fills the screen (keypad at the bottom) and scrolls when it doesn't fit, e.g. in landscape.
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val screenHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = screenHeight).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            LockBadge()
            Spacer(Modifier.height(28.dp))
            CtrlLabel(stringResource(R.string.lock_brand))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = CtrlColors.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) CtrlColors.Error else CtrlColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.height(20.dp).offset { IntOffset(shake.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = CtrlColors.Accent, strokeWidth = 2.dp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(maxOf(pin.length, LockVault.PIN_MIN)) { i ->
                            val cell = Modifier.size(14.dp).clip(RoundedCornerShape(5.dp))
                            if (i < pin.length) Box(cell.background(CtrlColors.Accent))
                            else Box(cell.border(2.dp, CtrlColors.PinEmpty, RoundedCornerShape(5.dp)))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
            Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { d ->
                            DigitKey(d.toString(), enabled, Modifier.weight(1f)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigit(d)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Bottom left: fingerprint when it's offered, otherwise OK / Next.
                    val keyMod = Modifier.weight(1f)
                    when {
                        onFingerprint != null -> Key(
                            keyMod,
                            enabled = enabled,
                            border = BorderStroke(1.dp, CtrlColors.Accent),
                            filled = false,
                            onClick = onFingerprint,
                        ) { Icon(Icons.Outlined.Fingerprint, stringResource(R.string.lock_use_fingerprint), Modifier.size(26.dp), tint = CtrlColors.Accent) }
                        else -> Key(
                            keyMod,
                            enabled = enabled && showConfirm,
                            border = BorderStroke(1.dp, if (enabled && showConfirm) CtrlColors.Accent else CtrlColors.CardBorder),
                            filled = false,
                            onClick = onConfirm,
                        ) {
                            Text(
                                confirmLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (enabled && showConfirm) CtrlColors.Accent else CtrlColors.Inactive,
                                maxLines = 1,
                            )
                        }
                    }
                    DigitKey("0", enabled, Modifier.weight(1f)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit('0')
                    }
                    Key(
                        Modifier.weight(1f),
                        enabled = enabled && pin.isNotEmpty(),
                        border = null,
                        filled = false,
                        onClick = onBackspace,
                    ) { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.lock_delete_digit), Modifier.size(24.dp), tint = CtrlColors.Muted) }
                }
            }
            Spacer(Modifier.height(16.dp))
            // With a fingerprint key on the pad, OK for PINs of other lengths moves down here.
            if (onFingerprint != null && showConfirm) {
                TextButton(onClick = onConfirm, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = CtrlColors.Accent)) { Text(confirmLabel) }
            }
            bottom()
        }
        }
    }
}

private val KeyShape = RoundedCornerShape(22.dp)

@Composable
private fun Key(
    modifier: Modifier,
    enabled: Boolean,
    border: BorderStroke?,
    filled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(68.dp)
            .clip(KeyShape)
            .then(if (filled) Modifier.background(CtrlColors.Surface) else Modifier)
            .then(if (border != null) Modifier.border(border, KeyShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Key(modifier, enabled = enabled, border = CtrlColors.CardStroke, filled = true, onClick = onClick) {
        Text(label, style = CtrlType.Key, color = if (enabled) CtrlColors.Text else CtrlColors.Inactive)
    }
}
