package org.commcare.app.network

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.app.viewmodel.ConnectIdTokenManager
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ConnectIdTokenManager's OAuth token caching, expiry, and refresh logic.
 *
 * Uses the JVM in-memory PlatformKeychainStore and a mock ConnectIdApi (via
 * mock PlatformHttpClient) to verify token lifecycle behavior without network calls.
 */
class ConnectIdTokenManagerTest {

    /** Mock HTTP client that returns configurable OAuth token responses. */
    private class MockOAuthHttpClient(
        private var tokenResponse: String = """{"access_token":"mock-token","expires_in":3600}""",
        private var responseCode: Int = 200
    ) : PlatformHttpClient {
        var requestCount = 0
            private set
        var lastRequest: HttpRequest? = null
            private set

        fun setNextResponse(token: String, expiresIn: Long) {
            tokenResponse = """{"access_token":"$token","expires_in":$expiresIn}"""
        }

        fun setFailure() {
            responseCode = 401
        }

        fun setSuccess() {
            responseCode = 200
        }

        override fun execute(request: HttpRequest): HttpResponse {
            requestCount++
            lastRequest = request
            return HttpResponse(
                code = responseCode,
                headers = emptyMap(),
                body = if (responseCode in 200..299) tokenResponse.encodeToByteArray() else null,
                errorBody = if (responseCode !in 200..299) "Unauthorized".encodeToByteArray() else null
            )
        }
    }

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun createManager(
        httpClient: MockOAuthHttpClient = MockOAuthHttpClient(),
        keychain: PlatformKeychainStore = PlatformKeychainStore()
    ): Triple<ConnectIdTokenManager, MockOAuthHttpClient, PlatformKeychainStore> {
        val db = createTestDatabase()
        val repo = ConnectIdRepository(db)
        val api = ConnectIdApi(httpClient)
        val manager = ConnectIdTokenManager(api, repo, keychain)
        return Triple(manager, httpClient, keychain)
    }

    // =====================================================================
    // getConnectIdToken — caching + expiry
    // =====================================================================

    @Test
    fun testGetConnectIdToken_returnsNull_whenNoCredentials() {
        val (manager, _, _) = createManager()
        // No username/password stored in keychain
        assertNull(manager.getConnectIdToken())
    }

    @Test
    fun testGetConnectIdToken_fetchesNewToken_whenNoCache() {
        val httpClient = MockOAuthHttpClient()
        httpClient.setNextResponse("fresh-token", 3600)
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "testuser")
        keychain.store("connect_password", "testpass")

        val (manager, _, _) = createManager(httpClient, keychain)

        val token = manager.getConnectIdToken()
        assertEquals("fresh-token", token)
        assertEquals(1, httpClient.requestCount)
    }

    @Test
    fun testGetConnectIdToken_returnsCached_whenNotExpired() {
        val httpClient = MockOAuthHttpClient()
        httpClient.setNextResponse("cached-token", 3600)
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "testuser")
        keychain.store("connect_password", "testpass")

        val (manager, _, _) = createManager(httpClient, keychain)

        // First call — fetches from API
        val token1 = manager.getConnectIdToken()
        assertEquals("cached-token", token1)
        assertEquals(1, httpClient.requestCount)

        // Second call — should return from cache without new API call
        httpClient.setNextResponse("new-token", 3600)
        val token2 = manager.getConnectIdToken()
        assertEquals("cached-token", token2)
        assertEquals(1, httpClient.requestCount, "Should not make a second API call when cache is valid")
    }

    @Test
    fun testGetConnectIdToken_refreshes_whenExpired() {
        val httpClient = MockOAuthHttpClient()
        httpClient.setNextResponse("first-token", 3600)
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "testuser")
        keychain.store("connect_password", "testpass")

        val (manager, _, _) = createManager(httpClient, keychain)

        // First call fetches token
        val token1 = manager.getConnectIdToken()
        assertEquals("first-token", token1)

        // Simulate expiry by setting the expiry to a past timestamp
        keychain.store("connect_token_expiry", "0")

        httpClient.setNextResponse("refreshed-token", 7200)
        val token2 = manager.getConnectIdToken()
        assertEquals("refreshed-token", token2)
        assertEquals(2, httpClient.requestCount, "Should make a second API call after expiry")
    }

    // =====================================================================
    // refreshConnectIdToken
    // =====================================================================

    @Test
    fun testRefreshConnectIdToken_returnsNull_whenNoCredentials() {
        val (manager, _, _) = createManager()
        assertNull(manager.refreshConnectIdToken())
    }

    @Test
    fun testRefreshConnectIdToken_returnsNull_whenMissingPassword() {
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "testuser")
        // No password stored

        val (manager, _, _) = createManager(keychain = keychain)
        assertNull(manager.refreshConnectIdToken())
    }

    @Test
    fun testRefreshConnectIdToken_returnsNull_whenApiFails() {
        val httpClient = MockOAuthHttpClient()
        httpClient.setFailure()
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "testuser")
        keychain.store("connect_password", "testpass")

        val (manager, _, _) = createManager(httpClient, keychain)
        assertNull(manager.refreshConnectIdToken())
    }

    @Test
    fun testRefreshConnectIdToken_storesTokenAndExpiry() {
        val httpClient = MockOAuthHttpClient()
        httpClient.setNextResponse("stored-token", 3600)
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "testuser")
        keychain.store("connect_password", "testpass")

        val (manager, _, _) = createManager(httpClient, keychain)

        val token = manager.refreshConnectIdToken()
        assertEquals("stored-token", token)

        // Verify token is stored in keychain
        assertEquals("stored-token", keychain.retrieve("connect_access_token"))
        // Verify expiry is stored (should be currentEpochSeconds + expiresIn - 60)
        val expiryStr = keychain.retrieve("connect_token_expiry")
        assertNotNull(expiryStr)
        val expiry = expiryStr.toLong()
        // The expiry should be a reasonable future timestamp (> current epoch)
        assertTrue(expiry > 0, "Expiry should be a positive timestamp")
    }

    // =====================================================================
    // isRegistered
    // =====================================================================

    @Test
    fun testIsRegistered_falseByDefault() {
        val (manager, _, _) = createManager()
        assertFalse(manager.isRegistered())
    }

    // =====================================================================
    // getStoredUsername
    // =====================================================================

    @Test
    fun testGetStoredUsername_nullWhenNotStored() {
        val (manager, _, _) = createManager()
        assertNull(manager.getStoredUsername())
    }

    @Test
    fun testGetStoredUsername_returnsStoredValue() {
        val keychain = PlatformKeychainStore()
        keychain.store("connect_username", "john@example.com")

        val (manager, _, _) = createManager(keychain = keychain)
        assertEquals("john@example.com", manager.getStoredUsername())
    }
}
