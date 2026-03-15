package org.commcare.app.platform

/**
 * iOS biometric auth stub.
 * Full implementation requires LocalAuthentication framework integration.
 * LAContext.evaluatePolicy for Face ID / Touch ID.
 */
actual class PlatformBiometricAuth actual constructor() {
    actual fun canAuthenticate(): Boolean {
        // TODO: Use LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics)
        return false
    }

    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        // TODO: Use LAContext().evaluatePolicy with callback
        onResult(BiometricResult.Unavailable)
    }
}
