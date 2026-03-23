package org.commcare.app.e2e

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.viewmodel.FormRecordViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for form draft save/resume workflow.
 */
class FormDraftTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    @Test
    fun testSaveDraftReturnsId() {
        val db = createTestDatabase()
        val frvm = FormRecordViewModel(db)

        val draftId = frvm.saveDraft(
            formXml = "<data><name>John</name></data>",
            xmlns = "http://test.form/1",
            formName = "Test Form"
        )

        assertTrue(draftId.isNotBlank(), "Draft should return a non-blank ID")
    }

    @Test
    fun testSaveDraftAndRetrieve() {
        val db = createTestDatabase()
        val frvm = FormRecordViewModel(db)

        val xml = "<data><name>Jane</name><age>30</age></data>"
        val draftId = frvm.saveDraft(xml, "http://test/form", "Patient Registration")

        val record = frvm.getRecord(draftId)
        assertNotNull(record, "Should retrieve the saved draft")
        assertEquals(draftId, record.formId)
    }

    @Test
    fun testUpdateExistingDraft() {
        val db = createTestDatabase()
        val frvm = FormRecordViewModel(db)

        val draftId1 = frvm.saveDraft("<data>v1</data>", "http://form", "Form")

        // Update the same draft
        val draftId2 = frvm.saveDraft("<data>v2</data>", "http://form", "Form", draftId1)
        assertEquals(draftId1, draftId2, "Updating existing draft should return same ID")
    }

    @Test
    fun testDeleteDraft() {
        val db = createTestDatabase()
        val frvm = FormRecordViewModel(db)

        val draftId = frvm.saveDraft("<data>temp</data>", "http://form", "Temp")
        assertNotNull(frvm.getRecord(draftId))

        frvm.deleteRecord(draftId)
        assertNull(frvm.getRecord(draftId), "Deleted draft should not be retrievable")
    }

    @Test
    fun testMultipleDraftsIndependent() {
        val db = createTestDatabase()
        val frvm = FormRecordViewModel(db)

        val id1 = frvm.saveDraft("<data>form1</data>", "http://form/1", "Form 1")
        val id2 = frvm.saveDraft("<data>form2</data>", "http://form/2", "Form 2")

        assertTrue(id1 != id2, "Different drafts should have different IDs")
        assertNotNull(frvm.getRecord(id1))
        assertNotNull(frvm.getRecord(id2))
    }
}
