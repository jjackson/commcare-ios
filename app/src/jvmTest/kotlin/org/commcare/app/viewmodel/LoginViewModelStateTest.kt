package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [LoginViewModel] state management.
 *
 * Covers the state transitions and domain resolution logic that
 * caused bugs #391 (resolveDomain hardcoded "demo"), #410 (relaunch
 * login race), and #416 (NeedsLogin bridge not triggering recomp).
 *
 * Phase 10 Stream 1 — ViewModel test backfill.
 */
class LoginViewModelStateTest {

    private fun createDb(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun makeApp(domain: String = "jonstest"): ApplicationRecord {
        return ApplicationRecord(
            id = "test-app-id",
            profileUrl = "https://www.commcarehq.org/a/$domain/apps/download/test/profile.ccpr",
            displayName = "Test App",
            domain = domain,
            majorVersion = 2,
            minorVersion = 53,
            installDate = 0L
        )
    }

    @Test
    fun initialAppStateIsLoggedOut() {
        val vm = LoginViewModel(createDb())
        assertTrue(vm.appState is AppState.LoggedOut)
    }

    @Test
    fun setReadyStateChangesAppState() {
        val vm = LoginViewModel(createDb())
        val app = makeApp()
        vm.setReadyState(AppState.NeedsLogin(app, listOf(app)))
        assertTrue(vm.appState is AppState.NeedsLogin)
    }

    @Test
    fun configureAppSetsDomainForResolveDomain() {
        val vm = LoginViewModel(createDb())
        vm.username = "haltest"
        val app = makeApp("jonstest")
        vm.configureApp("https://www.commcarehq.org", "test-app-id", app)

        assertEquals("jonstest", vm.resolveDomain(),
            "resolveDomain should use the installed app's domain")
    }

    @Test
    fun resolveDomainPrefersExplicitUsername() {
        val vm = LoginViewModel(createDb())
        vm.username = "haltest@otherdomain.commcarehq.org"
        val app = makeApp("jonstest")
        vm.configureApp("https://www.commcarehq.org", "test-app-id", app)

        assertEquals("otherdomain", vm.resolveDomain(),
            "explicit @domain in username should override installed app domain")
    }

    @Test
    fun resolveDomainFallsBackToDemoWithoutApp() {
        val vm = LoginViewModel(createDb())
        vm.username = "testuser"
        // No configureApp called

        assertEquals("demo", vm.resolveDomain(),
            "without an installed app, should fall back to demo")
    }

    @Test
    fun configureAppIsIdempotent() {
        val vm = LoginViewModel(createDb())
        vm.username = "haltest"
        val app = makeApp("jonstest")

        vm.configureApp("https://www.commcarehq.org", "test-app-id", app)
        assertEquals("jonstest", vm.resolveDomain())

        // Call again with same args — should not break
        vm.configureApp("https://www.commcarehq.org", "test-app-id", app)
        assertEquals("jonstest", vm.resolveDomain())
    }

    @Test
    fun resetErrorReturnsToLoggedOut() {
        val vm = LoginViewModel(createDb())
        vm.setReadyState(AppState.LoginError("test error"))
        assertTrue(vm.appState is AppState.LoginError)

        vm.resetError()
        assertTrue(vm.appState is AppState.LoggedOut)
    }
}
