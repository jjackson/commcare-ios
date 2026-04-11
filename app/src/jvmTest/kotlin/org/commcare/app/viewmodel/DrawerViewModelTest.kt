package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.app.model.ConnectIdUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [DrawerViewModel] state management.
 *
 * Covers the refresh() and switchApp() state transitions. The
 * seatedAppId field is set from both refresh() and switchApp(),
 * making it a round-trip test candidate.
 *
 * Phase 10 Stream 1 — ViewModel test backfill.
 */
class DrawerViewModelTest {

    private fun createDb(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun seedApp(db: CommCareDatabase, id: String, name: String, domain: String = "test") {
        db.commCareQueries.insertApp(
            id = id, profile_url = "", display_name = name,
            domain = domain, major_version = 2, minor_version = 53,
            status = "INSTALLED", resources_validated = 0,
            install_date = 0, banner_url = null, icon_url = null
        )
    }

    @Test
    fun refreshLoadsAppsAndSeatedId() {
        val db = createDb()
        seedApp(db, "app1", "App One")
        db.commCareQueries.setSeatedAppId("app1")
        val repo = AppRecordRepository(db)
        val vm = DrawerViewModel(repo)

        vm.refresh()

        assertEquals(1, vm.apps.size)
        assertEquals("App One", vm.apps[0].displayName)
        assertEquals("app1", vm.seatedAppId)
    }

    @Test
    fun switchAppUpdatesSeatedId() {
        val db = createDb()
        seedApp(db, "app1", "App One")
        seedApp(db, "app2", "App Two")
        db.commCareQueries.setSeatedAppId("app1")
        val repo = AppRecordRepository(db)
        val vm = DrawerViewModel(repo)

        vm.refresh()
        assertEquals("app1", vm.seatedAppId)

        vm.switchApp("app2")
        assertEquals("app2", vm.seatedAppId,
            "switchApp should update seatedAppId")
    }

    @Test
    fun refreshWithConnectIdShowsProfile() {
        val db = createDb()
        seedApp(db, "app1", "Test App")
        db.commCareQueries.setSeatedAppId("app1")
        val appRepo = AppRecordRepository(db)
        val connectRepo = ConnectIdRepository(db)

        // Save a Connect ID user
        connectRepo.saveUser(ConnectIdUser(
            userId = "test-user",
            name = "Hal Test",
            phone = "+74260000042",
            photoPath = null,
            hasConnectAccess = true
        ))

        val vm = DrawerViewModel(appRepo, connectRepo)
        vm.refresh()

        assertEquals("Hal Test", vm.profileName)
        assertEquals("+74260000042", vm.profilePhone)
        assertTrue(vm.hasConnectAccess)
    }

    @Test
    fun refreshWithoutConnectIdShowsNoProfile() {
        val db = createDb()
        seedApp(db, "app1", "Test App")
        db.commCareQueries.setSeatedAppId("app1")
        val appRepo = AppRecordRepository(db)

        val vm = DrawerViewModel(appRepo) // no ConnectIdRepository
        vm.refresh()

        assertNull(vm.profileName)
        assertNull(vm.profilePhone)
        assertFalse(vm.hasConnectAccess)
    }
}
