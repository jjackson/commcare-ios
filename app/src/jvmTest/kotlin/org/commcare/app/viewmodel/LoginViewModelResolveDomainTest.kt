package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.CommCareDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for [LoginViewModel.resolveDomain].
 *
 * Tracks issue #391: short-form usernames used to fall back to a hardcoded
 * "demo" domain regardless of what app was installed, causing silent 401
 * failures on any HQ project other than /a/demo/. The fix prefers the
 * installed app's domain over the hardcoded fallback when the username
 * has no @ suffix.
 */
class LoginViewModelResolveDomainTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun vmWithApp(
        username: String,
        appDomain: String?
    ): LoginViewModel {
        val vm = LoginViewModel(createTestDatabase())
        vm.username = username
        if (appDomain != null) {
            val app = ApplicationRecord(
                id = "test-app",
                profileUrl = "",
                displayName = "Test",
                domain = appDomain,
                majorVersion = 2,
                minorVersion = 53,
                installDate = 0L
            )
            vm.configureApp("https://www.commcarehq.org", "test-app", app)
        }
        return vm
    }

    @Test
    fun testFullyQualifiedUsernameExtractsDomain() {
        val vm = vmWithApp("haltest@jonstest.commcarehq.org", null)
        assertEquals("jonstest", vm.resolveDomain())
    }

    @Test
    fun testFullyQualifiedUsernameWithoutCommcarehqSuffix() {
        val vm = vmWithApp("user@some-other-host", null)
        assertEquals("some-other-host", vm.resolveDomain())
    }

    @Test
    fun testShortUsernamePrefersInstalledAppDomain() {
        // Repro for #391 — was returning "demo", should return "jonstest".
        val vm = vmWithApp("haltest", appDomain = "jonstest")
        assertEquals("jonstest", vm.resolveDomain())
    }

    @Test
    fun testShortUsernameFallsBackToDemoWhenNoApp() {
        val vm = vmWithApp("haltest", appDomain = null)
        assertEquals("demo", vm.resolveDomain())
    }

    @Test
    fun testShortUsernameFallsBackToDemoWhenAppDomainIsBlank() {
        val vm = vmWithApp("haltest", appDomain = "")
        assertEquals("demo", vm.resolveDomain())
    }

    @Test
    fun testFullyQualifiedUsernameIgnoresInstalledAppDomain() {
        // Explicit @-suffix wins even if an app is installed with a different
        // domain — the user's typed-in intent takes priority.
        val vm = vmWithApp("haltest@jonstest.commcarehq.org", appDomain = "some-other-project")
        assertEquals("jonstest", vm.resolveDomain())
    }

    /*
     * The resolveDomain() unit tests above cover domain routing. The full
     * short-form username auth flow (expansion to `user@domain.commcarehq.org`
     * before Basic auth encoding) is verified end-to-end by the Phase 9
     * wave3 orchestrator running against the `haltest` short form on jonstest.
     * See `.maestro/scripts/run-wave3.sh` and `.env.e2e.local.example`.
     */
}
