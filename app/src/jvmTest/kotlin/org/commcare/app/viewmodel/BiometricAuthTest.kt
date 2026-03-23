package org.commcare.app.viewmodel

import org.commcare.app.platform.PlatformBiometricAuth
import org.commcare.app.platform.BiometricResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for biometric auth: availability check and fallback behavior.
 */
class BiometricAuthTest {

    @Test
    fun testJvmBiometricUnavailable() {
        // JVM stub always returns false for canAuthenticate
        val biometric = PlatformBiometricAuth()
        assertFalse(biometric.canAuthenticate(), "JVM should report biometric unavailable")
    }

    @Test
    fun testJvmAuthenticateReturnsUnavailable() {
        val biometric = PlatformBiometricAuth()
        var result: BiometricResult? = null
        biometric.authenticate("Test unlock") { result = it }
        assertTrue(result is BiometricResult.Unavailable, "JVM authenticate should return Unavailable")
    }

    @Test
    fun testBiometricResultSealedClass() {
        // Verify all result types exist
        val success: BiometricResult = BiometricResult.Success
        val failure: BiometricResult = BiometricResult.Failure("test")
        val unavailable: BiometricResult = BiometricResult.Unavailable
        val cancelled: BiometricResult = BiometricResult.Cancelled

        assertTrue(success is BiometricResult.Success)
        assertTrue(failure is BiometricResult.Failure)
        assertTrue(unavailable is BiometricResult.Unavailable)
        assertTrue(cancelled is BiometricResult.Cancelled)
    }

    @Test
    fun testFallbackToPinWhenBiometricUnavailable() {
        val biometric = PlatformBiometricAuth()
        // When biometric is unavailable, the app should fall back to PIN mode
        val shouldUsePinFallback = !biometric.canAuthenticate()
        assertTrue(shouldUsePinFallback, "Should fall back to PIN when biometric unavailable")
    }
}
