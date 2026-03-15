package org.commcare.app.platform

/**
 * Platform-specific biometric authentication (Face ID, Touch ID on iOS).
 * JVM actual is a stub that always returns unavailable.
 */
expect class PlatformBiometricAuth() {
    /**
     * Check if biometric authentication is available on this device.
     */
    fun canAuthenticate(): Boolean

    /**
     * Authenticate the user using biometrics.
     * @param reason Reason string displayed to the user
     * @param onResult Callback with success/failure result
     */
    fun authenticate(reason: String, onResult: (BiometricResult) -> Unit)
}

sealed class BiometricResult {
    data object Success : BiometricResult()
    data class Failure(val message: String) : BiometricResult()
    data object Unavailable : BiometricResult()
    data object Cancelled : BiometricResult()
}
