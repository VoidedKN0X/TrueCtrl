package com.vknxworks.truectrl.ui.lock

import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.lock.AppLock
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.PillButton

/** Covers the app while it's locked and asks for the fingerprint, face or screen lock. */
@Composable
fun LockScreen() {
    val context = LocalContext.current
    val c = context.container
    var prompting by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf<CancellationSignal?>(null) }

    fun prompt() {
        if (prompting) return
        // No screen lock any more (removed in Android's settings): nothing to check against.
        if (!AppLock.available(context)) {
            c.appLock.unlocked()
            return
        }
        prompting = true
        val signal = CancellationSignal()
        cancel = signal
        BiometricPrompt.Builder(context)
            .setTitle(context.getString(R.string.lock_prompt_title))
            .setSubtitle(context.getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
            .build()
            .authenticate(
                signal,
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        prompting = false
                        c.appLock.unlocked()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        prompting = false
                    }
                },
            )
    }

    LaunchedEffect(Unit) { prompt() }
    DisposableEffect(Unit) { onDispose { cancel?.cancel() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so nothing underneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        EmptyState(
            Icons.Outlined.Lock,
            stringResource(R.string.lock_title),
            stringResource(R.string.lock_body),
            action = { PillButton(stringResource(R.string.lock_unlock), onClick = ::prompt) },
        )
    }
}
