package org.commcare.app.platform

/**
 * JVM stub for biometric auth — always unavailable.
 */
actual class PlatformBiometricAuth actual constructor() {
    actual fun canAuthenticate(): Boolean = false

    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        onResult(BiometricResult.Unavailable)
    }
}
