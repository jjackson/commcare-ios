package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for multi-app switching via AppRecordRepository.
 */
class MultiAppSwitchTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun makeApp(id: String, name: String): ApplicationRecord = ApplicationRecord(
        id = id, profileUrl = "https://hq/$id/profile.xml",
        displayName = name, domain = "test.commcarehq.org",
        majorVersion = 1, installDate = 1000L
    )

    @Test
    fun testInstallAndRetrieveApp() {
        val db = createTestDatabase()
        val repo = AppRecordRepository(db)

        repo.insertApp(makeApp("app-1", "App One"))

        val app = repo.getAppById("app-1")
        assertNotNull(app)
        assertEquals("App One", app.displayName)
    }

    @Test
    fun testSeatAndSwitchApps() {
        val db = createTestDatabase()
        val repo = AppRecordRepository(db)

        repo.insertApp(makeApp("app-a", "App A"))
        repo.insertApp(makeApp("app-b", "App B"))
        repo.seatApp("app-a")

        assertEquals("app-a", repo.getSeatedApp()?.id)

        repo.seatApp("app-b")
        assertEquals("app-b", repo.getSeatedApp()?.id)
    }

    @Test
    fun testGetAllApps() {
        val db = createTestDatabase()
        val repo = AppRecordRepository(db)

        repo.insertApp(makeApp("app-1", "First"))
        repo.insertApp(makeApp("app-2", "Second"))

        assertEquals(2, repo.getAllApps().size)
        assertEquals(2, repo.getAppCount())
    }

    @Test
    fun testDeleteApp() {
        val db = createTestDatabase()
        val repo = AppRecordRepository(db)

        repo.insertApp(makeApp("app-del", "Delete Me"))
        assertNotNull(repo.getAppById("app-del"))

        repo.deleteApp("app-del")
        assertNull(repo.getAppById("app-del"))
    }

    @Test
    fun testNoSeatedAppInitially() {
        val db = createTestDatabase()
        val repo = AppRecordRepository(db)
        assertNull(repo.getSeatedApp())
    }

    @Test
    fun testArchiveApp() {
        val db = createTestDatabase()
        val repo = AppRecordRepository(db)

        repo.insertApp(makeApp("app-arc", "Archive Me"))
        repo.archiveApp("app-arc")

        val app = repo.getAppById("app-arc")
        assertNotNull(app)
        assertEquals(true, app.isArchived())
    }
}
