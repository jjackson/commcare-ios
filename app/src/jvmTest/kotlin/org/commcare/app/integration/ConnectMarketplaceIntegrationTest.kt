// app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration tests against the Connect Marketplace API.
 * Relocated from Phase 8 Task 3a. See ConnectIdIntegrationTest header comment.
 */
class ConnectMarketplaceIntegrationTest {

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
    fun testGetOpportunitiesReturnsValidList() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.getOpportunities(token)
        assertTrue(
            result.isSuccess,
            "getOpportunities should succeed: ${result.exceptionOrNull()}"
        )
        // The list may be empty if the fixture user has no opportunities,
        // but the call should not error.
    }

    @Test
    fun testGetOpportunitiesWithInvalidToken() {
        val result = api.getOpportunities("invalid-token-zzzz")
        assertTrue(result.isFailure, "Invalid token should fail")
    }
}
