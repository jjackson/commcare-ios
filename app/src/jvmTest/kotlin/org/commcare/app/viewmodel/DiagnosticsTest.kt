package org.commcare.app.viewmodel

import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for DiagnosticsViewModel: server ping and auth validation.
 */
class DiagnosticsTest {

    private class MockHttpClient(
        private val responseCode: Int = 200
    ) : PlatformHttpClient {
        override fun execute(request: HttpRequest): HttpResponse {
            return HttpResponse(responseCode, emptyMap(), "OK".encodeToByteArray())
        }
    }

    @Test
    fun testDiagnosticsRunsChecks() {
        val client = MockHttpClient(200)
        val vm = DiagnosticsViewModel(client, "https://hq.example.com", "test", "Basic dGVzdA==")

        vm.runDiagnostics(lastSyncTime = "2026-03-23", pendingFormCount = 2)

        // Wait for async
        Thread.sleep(1000)

        assertTrue(vm.results.isNotEmpty(), "Should produce diagnostic results")
    }

    @Test
    fun testDiagnosticsInitialState() {
        val client = MockHttpClient(200)
        val vm = DiagnosticsViewModel(client, "https://hq.example.com", "test", "Basic dGVzdA==")

        assertFalse(vm.isRunning, "Should not be running initially")
        assertTrue(vm.results.isEmpty(), "Should have no results initially")
    }

    @Test
    fun testDiagnosticsWithServerError() {
        val client = MockHttpClient(500)
        val vm = DiagnosticsViewModel(client, "https://hq.example.com", "test", "Basic dGVzdA==")

        vm.runDiagnostics(null, 0)
        Thread.sleep(1000)

        // Should still produce results (server reachable but error)
        assertTrue(vm.results.isNotEmpty())
    }
}
