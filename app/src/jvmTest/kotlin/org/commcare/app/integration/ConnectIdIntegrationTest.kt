// app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectIdApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration tests against connectid.dimagi.com.
 *
 * Requires CONNECT_ACCESS_TOKEN (and/or CONNECT_USERNAME + CONNECT_PASSWORD)
 * to be set in the environment. Tests are skipped via JUnit Assume if no
 * credentials are configured, so they pass cleanly in unconfigured CI.
 *
 * These tests were originally Phase 8 Task 2 and were deferred until the
 * +7426 / generate_manual_otp infrastructure existed. Phase 9 Wave 0
 * provides that infrastructure; this file relocates the tests here.
 */
class ConnectIdIntegrationTest {

    private lateinit var api: ConnectIdApi

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Connect credentials not configured (set CONNECT_ACCESS_TOKEN)",
            ConnectTestConfig.isConfigured
        )
        api = ConnectIdApi(createHttpClient())
    }

    @Test
    fun testFetchDbKeyWithValidToken() {
        val token = ConnectTestConfig.connectAccessToken
        Assume.assumeTrue("CONNECT_ACCESS_TOKEN not set", token.isNotBlank())

        val result = api.fetchDbKey(token)
        assertTrue(
            result.isSuccess,
            "fetchDbKey should succeed with a valid token: ${result.exceptionOrNull()}"
        )
        val dbKey = result.getOrNull()
        assertTrue(dbKey != null && dbKey.isNotBlank(), "DB key should be non-blank")
    }

    @Test
    fun testFetchDbKeyWithExpiredToken() {
        val result = api.fetchDbKey("expired-invalid-token-12345")
        assertTrue(result.isFailure, "Expired token should fail")
    }

    @Test
    fun testOAuthTokenWithInvalidCredentials() {
        val result = api.getOAuthToken("nonexistent@user.example", "wrongpassword")
        assertTrue(result.isFailure, "Invalid credentials should fail")
    }
}
