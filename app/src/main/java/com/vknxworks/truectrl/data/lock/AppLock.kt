package com.vknxworks.truectrl.data.lock

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The optional app lock. When it's on, TrueCtrl is locked at start and every time it goes to the
 * background, and opens again with the fingerprint, face or the phone's screen lock.
 */
class AppLock(private val context: Context, enabledAtStart: Boolean) {
    private val _locked = MutableStateFlow(enabledAtStart)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun lock() {
        _locked.value = true
    }

    fun unlocked() {
        _locked.value = false
    }

    companion object {
        /** Strong biometrics, or the PIN, pattern or password as a fallback. */
        const val AUTHENTICATORS = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL

        /** The phone has a screen lock (and maybe biometrics) that can unlock the app. */
        fun available(context: Context): Boolean =
            context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
