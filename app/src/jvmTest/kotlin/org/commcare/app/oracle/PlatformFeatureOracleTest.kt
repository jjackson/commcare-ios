package org.commcare.app.oracle

import org.commcare.app.platform.BiometricResult
import org.commcare.app.platform.PlatformBiometricAuth
import org.commcare.app.viewmodel.AppUpdateStatus
import org.commcare.app.viewmodel.DiagnosticResult
import org.commcare.app.viewmodel.DiagnosticStatus
import org.commcare.app.viewmodel.HeartbeatState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Oracle tests for authentication and platform features.
 */
class PlatformFeatureOracleTest {

    @Test
    fun testBiometricAuthJvmUnavailable() {
        val auth = PlatformBiometricAuth()
        assertFalse(auth.canAuthenticate())
    }

    @Test
    fun testBiometricAuthJvmReturnsUnavailable() {
        val auth = PlatformBiometricAuth()
        var result: BiometricResult? = null
        auth.authenticate("Test") { result = it }
        assertIs<BiometricResult.Unavailable>(result)
    }

    @Test
    fun testBiometricResultTypes() {
        val results = listOf(
            BiometricResult.Success,
            BiometricResult.Failure("test"),
            BiometricResult.Unavailable,
            BiometricResult.Cancelled
        )
        assertEquals(4, results.size)
        assertEquals("test", (results[1] as BiometricResult.Failure).message)
    }

    @Test
    fun testHeartbeatStates() {
        val states = listOf(
            HeartbeatState.Idle,
            HeartbeatState.Sending,
            HeartbeatState.Success,
            HeartbeatState.Error("network error")
        )
        assertEquals(4, states.size)
        assertEquals("network error", (states[3] as HeartbeatState.Error).message)
    }

    @Test
    fun testAppUpdateStatusTypes() {
        val statuses = listOf(
            AppUpdateStatus.UpToDate,
            AppUpdateStatus.UpdateAvailable,
            AppUpdateStatus.ForceUpdate
        )
        assertEquals(3, statuses.size)
    }

    @Test
    fun testDiagnosticResultModel() {
        val result = DiagnosticResult(
            name = "Server Ping",
            status = DiagnosticStatus.OK,
            detail = "Server reachable"
        )
        assertEquals("Server Ping", result.name)
        assertEquals(DiagnosticStatus.OK, result.status)
        assertEquals("Server reachable", result.detail)
    }

    @Test
    fun testDiagnosticStatusValues() {
        val statuses = DiagnosticStatus.entries
        assertEquals(3, statuses.size)
        assertTrue(statuses.contains(DiagnosticStatus.OK))
        assertTrue(statuses.contains(DiagnosticStatus.Warning))
        assertTrue(statuses.contains(DiagnosticStatus.Error))
    }
}
