@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorUserCancel
import platform.Foundation.NSError
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
        var policyError: NSError? = null
        val canEvaluate = context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = policyError
        )

        if (!canEvaluate) {
            val code = policyError?.code
            when (code) {
                LAErrorBiometryNotAvailable, LAErrorBiometryNotEnrolled ->
                    onResult(BiometricResult.Unavailable)
                else ->
                    onResult(BiometricResult.Failure(policyError?.localizedDescription ?: "Biometrics not available"))
            }
            return
        }

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason
        ) { success, error ->
            dispatch_async(dispatch_get_main_queue()) {
                when {
                    success -> onResult(BiometricResult.Success)
                    error?.code == LAErrorUserCancel -> onResult(BiometricResult.Cancelled)
                    error?.code == LAErrorBiometryNotAvailable -> onResult(BiometricResult.Unavailable)
                    else -> onResult(BiometricResult.Failure(error?.localizedDescription ?: "Authentication failed"))
                }
            }
        }
    }
}
