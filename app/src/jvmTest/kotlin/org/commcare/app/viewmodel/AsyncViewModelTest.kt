package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for async ViewModel behavior — verifying that login(), sync(), and submitAll()
 * return immediately without blocking the calling thread.
 */
class AsyncViewModelTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    /**
     * A slow HTTP client that blocks for a configurable duration.
     * Used to verify that ViewModels don't block the calling thread.
     */
    private class SlowHttpClient(private val delayMs: Long = 500) : PlatformHttpClient {
        var requestCount = 0
            private set

        override fun execute(request: HttpRequest): HttpResponse {
            requestCount++
            Thread.sleep(delayMs)
            return HttpResponse(
                code = 200,
                body = "<restoredata><restore_id>token-1</restore_id></restoredata>".encodeToByteArray(),
                headers = emptyMap()
            )
        }
    }

    @Test
    fun testLoginReturnsImmediately() {
        val db = createTestDatabase()
        val viewModel = LoginViewModel(db)
        viewModel.username = "user@demo"
        viewModel.password = "pass"

        val start = System.currentTimeMillis()
        viewModel.login()
        val elapsed = System.currentTimeMillis() - start

        // login() should return immediately (coroutine launched in background)
        // The actual HTTP call takes 500ms+ in SlowHttpClient, so if login() blocks
        // it would take at least that long. We allow 200ms for coroutine setup.
        assertTrue(elapsed < 200, "login() should return immediately, took ${elapsed}ms")

        // State should be LoggingIn (set before coroutine launch)
        assertIs<AppState.LoggingIn>(viewModel.appState)
    }

    @Test
    fun testLoginValidationIsImmediate() {
        val db = createTestDatabase()
        val viewModel = LoginViewModel(db)
        // Empty credentials
        viewModel.login()
        assertIs<AppState.LoginError>(viewModel.appState)
        assertEquals("Username and password are required", (viewModel.appState as AppState.LoginError).message)
    }

    @Test
    fun testSyncReturnsImmediately() {
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)
        val httpClient = SlowHttpClient(delayMs = 500)
        val syncViewModel = SyncViewModel(
            httpClient = httpClient,
            serverUrl = "https://example.com",
            domain = "demo",
            authHeader = "Basic dGVzdDp0ZXN0",
            sandbox = sandbox
        )

        val start = System.currentTimeMillis()
        syncViewModel.sync()
        val elapsed = System.currentTimeMillis() - start

        // sync() should return immediately
        assertTrue(elapsed < 200, "sync() should return immediately, took ${elapsed}ms")
        // State should be Syncing (set before coroutine launch)
        assertIs<SyncState.Syncing>(syncViewModel.syncState)
    }

    @Test
    fun testFormQueueSubmitAllReturnsImmediately() {
        val httpClient = SlowHttpClient(delayMs = 500)
        val formQueue = FormQueueViewModel(
            httpClient = httpClient,
            serverUrl = "https://example.com",
            domain = "demo",
            authHeader = "Basic dGVzdDp0ZXN0"
        )

        formQueue.enqueueForm("<data/>", "Test Form")
        assertEquals(1, formQueue.pendingCount)

        val start = System.currentTimeMillis()
        formQueue.submitAll()
        val elapsed = System.currentTimeMillis() - start

        // submitAll() should return immediately (async)
        assertTrue(elapsed < 200, "submitAll() should return immediately, took ${elapsed}ms")
    }

    @Test
    fun testFormQueueSubmitAllSyncIsBlocking() {
        val httpClient = SlowHttpClient(delayMs = 100)
        val formQueue = FormQueueViewModel(
            httpClient = httpClient,
            serverUrl = "https://example.com",
            domain = "demo",
            authHeader = "Basic dGVzdDp0ZXN0"
        )

        formQueue.enqueueForm("<data/>", "Test Form")

        val start = System.currentTimeMillis()
        val submitted = formQueue.submitAllSync()
        val elapsed = System.currentTimeMillis() - start

        // submitAllSync() is synchronous and should block
        assertTrue(elapsed >= 100, "submitAllSync() should block, took ${elapsed}ms")
        assertEquals(1, submitted)
    }
}
