package org.commcare.app.platform

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Foundation.NSError

/**
 * iOS biometric auth using LocalAuthentication framework (Face ID / Touch ID).
 */
actual class PlatformBiometricAuth actual constructor() {
    actual fun canAuthenticate(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null
        )
    }

    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        val context = LAContext()
        if (!canAuthenticate()) {
            onResult(BiometricResult.Unavailable)
            return
        }

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason
        ) { success, error ->
            when {
                success -> onResult(BiometricResult.Success)
                error != null -> {
                    val code = error.code
                    if (code == -2L) { // LAErrorUserCancel
                        onResult(BiometricResult.Cancelled)
                    } else {
                        onResult(BiometricResult.Failure(error.localizedDescription ?: "Authentication failed"))
                    }
                }
                else -> onResult(BiometricResult.Failure("Unknown error"))
            }
        }
    }
}
