package org.commcare.app.viewmodel

import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for HeartbeatManager: device status reporting and force_update detection.
 */
class HeartbeatManagerTest {

    private class MockHttpClient(
        private val responseCode: Int = 200,
        private val responseBody: String = "{}"
    ) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            return HttpResponse(responseCode, emptyMap(), responseBody.encodeToByteArray())
        }
    }

    @Test
    fun testHeartbeatSendsRequest() {
        val client = MockHttpClient(200, """{"force_update": false}""")
        val manager = HeartbeatManager(client, "https://hq.example.com", "test-domain", "Basic dGVzdA==")

        manager.sendHeartbeat("42", "2026-03-23T00:00:00Z", 3)
        Thread.sleep(1000)

        assertTrue(client.requests.isNotEmpty(), "Should send at least one HTTP request")
        val request = client.requests.first()
        assertTrue(request.url.contains("heartbeat"), "URL should contain 'heartbeat'")
    }

    @Test
    fun testHeartbeatParsesForceUpdate() {
        val client = MockHttpClient(200, """{"force_update": true}""")
        val manager = HeartbeatManager(client, "https://hq.example.com", "test-domain", "Basic dGVzdA==")

        manager.sendHeartbeat("42", null, 0)
        Thread.sleep(1000)

        assertTrue(manager.isForceUpdateRequired(), "Should detect force_update: true")
    }

    @Test
    fun testHeartbeatNoForceUpdate() {
        val client = MockHttpClient(200, """{"force_update": false}""")
        val manager = HeartbeatManager(client, "https://hq.example.com", "test-domain", "Basic dGVzdA==")

        manager.sendHeartbeat("42", null, 0)

        assertFalse(manager.isForceUpdateRequired(), "Should not require force update")
    }

    @Test
    fun testHeartbeatHandlesServerError() {
        val client = MockHttpClient(500, "Server Error")
        val manager = HeartbeatManager(client, "https://hq.example.com", "test-domain", "Basic dGVzdA==")

        // Should not throw
        manager.sendHeartbeat("1", null, 0)
        assertFalse(manager.isForceUpdateRequired())
    }
}
