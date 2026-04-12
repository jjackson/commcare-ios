package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.model.MessageThread
import org.commcare.app.network.ConnectIdApi
import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [MessagingViewModel] state management.
 *
 * Covers consent flow, thread loading, thread selection, and error
 * handling. Uses mock HTTP clients.
 *
 * Phase 10 Stream 1 — ViewModel test backfill.
 */
class MessagingViewModelTest {

    private class MockClient(
        var responseBody: String = """{"channels": [], "messages": []}""",
        var responseCode: Int = 200
    ) : PlatformHttpClient {
        var requestCount = 0
        override fun execute(request: HttpRequest): HttpResponse {
            requestCount++
            return HttpResponse(
                code = responseCode,
                body = responseBody.encodeToByteArray(),
                headers = emptyMap()
            )
        }
    }

    private class MockTokenClient : PlatformHttpClient {
        override fun execute(request: HttpRequest): HttpResponse {
            return HttpResponse(
                code = 200,
                body = """{"access_token":"test-token","expires_in":3600}""".encodeToByteArray(),
                headers = emptyMap()
            )
        }
    }

    private fun createDb(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun createTokenManager(db: CommCareDatabase): ConnectIdTokenManager {
        val keychainStore = PlatformKeychainStore()
        keychainStore.store("connect_username", "test")
        keychainStore.store("connect_password", "test")
        return ConnectIdTokenManager(
            ConnectIdApi(MockTokenClient()),
            ConnectIdRepository(db),
            keychainStore,
            db
        )
    }

    private fun createVm(client: MockClient = MockClient()): MessagingViewModel {
        val db = createDb()
        return MessagingViewModel(
            ConnectMarketplaceApi(client),
            createTokenManager(db)
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createVm()
        assertTrue(vm.threads.isEmpty())
        assertNull(vm.selectedThread)
        assertFalse(vm.isLoading)
        assertFalse(vm.hasConsented)
        assertNull(vm.errorMessage)
    }

    @Test
    fun loadThreadsPopulatesFromServer() {
        val client = MockClient(
            responseBody = """{"channels": [], "messages": [
                {"id": "t1", "participant_name": "Alice", "last_message": "Hi", "last_message_date": "2026-04-10", "unread_count": 1}
            ]}"""
        )
        val vm = createVm(client)
        vm.loadThreads()
        Thread.sleep(200)

        assertEquals(1, vm.threads.size)
        assertEquals("Alice", vm.threads[0].participantName)
        assertEquals(1, vm.unreadCount)
        // Threads present → should auto-set hasConsented
        assertTrue(vm.hasConsented)
    }

    @Test
    fun loadThreadsEmptyDoesNotSetConsented() {
        val vm = createVm()
        vm.loadThreads()
        Thread.sleep(200)

        assertTrue(vm.threads.isEmpty())
        assertFalse(vm.hasConsented)
    }

    @Test
    fun selectThreadUpdatesState() {
        val thread = MessageThread("t1", "Alice", "Hi", "2026-04-10", 0)
        val vm = createVm()
        vm.selectThread(thread)
        assertEquals("t1", vm.selectedThread?.id)
    }

    @Test
    fun clearThreadResetsSelection() {
        val thread = MessageThread("t1", "Alice", "Hi", "2026-04-10", 0)
        val vm = createVm()
        vm.selectThread(thread)
        assertEquals("t1", vm.selectedThread?.id)

        vm.clearThread()
        assertNull(vm.selectedThread)
    }

    @Test
    fun updateConsentWithNoChannelsShowsError() {
        val vm = createVm() // empty channels
        vm.loadThreads() // populate availableChannels (empty)
        Thread.sleep(200)

        vm.updateConsent()
        Thread.sleep(200)

        assertTrue(vm.errorMessage?.contains("No messaging channels") == true,
            "should report no channels, got: ${vm.errorMessage}")
    }

    @Test
    fun errorMessageClearedByLoad() {
        val client = MockClient(responseCode = 500)
        val vm = createVm(client)
        vm.loadThreads()
        Thread.sleep(200)

        assertTrue(vm.errorMessage != null, "should have error on 500")

        // Fix the client and reload
        client.responseCode = 200
        client.responseBody = """{"channels": [], "messages": []}"""
        vm.loadThreads()
        Thread.sleep(200)

        assertNull(vm.errorMessage, "error should be cleared on successful reload")
    }
}
