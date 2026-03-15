package org.commcare.app.engine

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.InMemoryStorage
import org.commcare.app.storage.SqlDelightUserSandbox
import org.javarosa.core.model.FormDef
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests that FormDef can be stored in and retrieved from InMemoryStorage by xmlns,
 * validating the form loading pipeline used by HomeScreen.loadFormEntry().
 */
class FormLoadingTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    @Test
    fun testFormDefStorageAndRetrievalByXmlns() {
        // Parse a real form
        val stream = this::class.java.getResourceAsStream("/test_form_entry_controller.xml")
        assertNotNull(stream)
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)

        // Store it in InMemoryStorage (same as AppInstaller uses)
        val storage = InMemoryStorage<FormDef>(FormDef::class, { FormDef() })
        storage.write(formDef)

        // Retrieve by XMLNS metadata
        val xmlns = formDef.getInstance()!!.schema
        assertNotNull(xmlns, "FormDef should have an xmlns")

        @Suppress("UNCHECKED_CAST")
        val loaded = storage.getRecordForValue("XMLNS", xmlns)
        assertNotNull(loaded, "Should find FormDef by XMLNS")
        assertEquals(xmlns, loaded.getInstance()!!.schema)
    }

    @Test
    fun testAppInstallerCreatesStorageManager() {
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)
        val installer = AppInstaller(sandbox)

        // createMinimalPlatform should set up a StorageManager
        val platform = installer.createMinimalPlatform()
        assertNotNull(platform.getStorageManager(), "Platform should have a StorageManager")

        // FormDef storage should be registered
        val formStorage = platform.getStorageManager()!!.getStorage(FormDef.STORAGE_KEY)
        assertNotNull(formStorage, "FormDef storage should be registered")
        assertEquals(0, formStorage.getNumRecords(), "Should start empty")
    }

    @Test
    fun testFormDefRoundTripThroughPlatformStorage() {
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)
        val installer = AppInstaller(sandbox)
        val platform = installer.createMinimalPlatform()

        // Parse a form and store it in platform storage
        val stream = this::class.java.getResourceAsStream("/test_form_entry_controller.xml")
        assertNotNull(stream)
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)

        @Suppress("UNCHECKED_CAST")
        val formStorage = platform.getStorageManager()!!.getStorage(FormDef.STORAGE_KEY) as IStorageUtilityIndexed<FormDef>
        formStorage.write(formDef)

        // Retrieve by XMLNS — this is the exact code path HomeScreen.loadFormEntry uses
        val xmlns = formDef.getInstance()!!.schema!!
        val loaded = formStorage.getRecordForValue("XMLNS", xmlns)
        assertNotNull(loaded)
        assertEquals(xmlns, loaded.getInstance()!!.schema)

        // Verify it can be initialized for form entry
        val session = FormEntrySession(loaded)
        session.initialize()
        assertTrue(session.getQuestionCount() > 0, "Loaded FormDef should have questions")
    }
}
