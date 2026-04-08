// app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration tests for Connect messaging APIs.
 *
 * Relocated from Phase 8 Task 3b. Note: messaging endpoints
 * (getMessages, sendMessage, updateConsent) live on
 * ConnectMarketplaceApi, not a separate client. This test uses
 * ConnectMarketplaceApi intentionally — see Phase 8 plan note.
 */
class ConnectMessagingIntegrationTest {

    private lateinit var api: ConnectMarketplaceApi

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Connect credentials not configured (set CONNECT_ACCESS_TOKEN)",
            ConnectTestConfig.isConfigured
        )
        api = ConnectMarketplaceApi(createHttpClient())
    }

    @Test
    fun testGetMessagesReturnsThreadList() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.getMessages(token)
        assertTrue(
            result.isSuccess,
            "getMessages should succeed: ${result.exceptionOrNull()}"
        )
    }

    @Test
    fun testUpdateConsentSucceeds() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.updateConsent(token)
        assertTrue(
            result.isSuccess,
            "updateConsent should succeed: ${result.exceptionOrNull()}"
        )
    }

    @Test
    fun testGetMessagesWithInvalidToken() {
        val result = api.getMessages("invalid-token-zzzz")
        assertTrue(result.isFailure, "Invalid token should fail")
    }
}
