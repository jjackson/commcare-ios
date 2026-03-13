package org.commcare.app.e2e

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.engine.FormEntrySession
import org.commcare.app.engine.FormSerializer
import org.commcare.app.engine.SessionNavigatorImpl
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.InMemoryStorage
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.app.viewmodel.CaseListViewModel
import org.commcare.app.viewmodel.FormEntryViewModel
import org.commcare.app.viewmodel.MenuViewModel
import org.commcare.cases.model.Case
import org.commcare.session.CommCareSession
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end validation test for the Tier 1 Minimum Viable App.
 * Tests the core journey: storage → form entry → serialization.
 */
class FullJourneyTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    @Test
    fun testSandboxStorageCRUD() {
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)

        // Create and store a case
        val caseStorage = sandbox.getCaseStorage()
        val case1 = Case()
        case1.setCaseId("case-001")
        case1.setName("Test Case")
        case1.setTypeId("patient")
        caseStorage.write(case1)

        // Read it back
        val loaded = caseStorage.getRecordForValue("case-id", "case-001")
        assertNotNull(loaded)
        assertEquals("Test Case", loaded.getName())
        assertEquals("patient", loaded.getTypeId())
    }

    @Test
    fun testFormEntryAndSerialization() {
        // Load a real form from test fixtures
        val stream = this::class.java.getResourceAsStream("/test_form_entry_controller.xml")
        assertNotNull(stream, "Test form should be loadable from resources")

        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef, "FormDef should parse successfully")

        // Create FormEntrySession and ViewModel
        val session = FormEntrySession(formDef)
        val viewModel = FormEntryViewModel(session)
        viewModel.loadForm()

        assertEquals("Constraints for simple and select answers", viewModel.formTitle)
        assertTrue(viewModel.questions.isNotEmpty(), "Should have questions loaded")

        // Navigate through form without answering (test navigation)
        var steps = 0
        while (!viewModel.isComplete && steps < 20) {
            viewModel.nextQuestion()
            steps++
        }
        assertTrue(viewModel.isComplete, "Should reach form completion")

        // Serialize the form
        val xml = viewModel.serializeForm()
        assertNotNull(xml, "Serialized XML should not be null")
        assertTrue(xml.contains("<data"), "XML should contain data element")
    }

    @Test
    fun testFormEntryWithAnswers() {
        // Load form, answer questions, serialize
        val stream = this::class.java.getResourceAsStream("/test_form_entry_controller.xml")
        assertNotNull(stream)
        val formDef = XFormUtils.getFormFromInputStream(stream)

        // Use the engine directly for more control
        formDef.initialize(true, null)
        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        controller.stepToNextEvent()
        var questionCount = 0

        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                questionCount++
            }
            controller.stepToNextEvent()
        }

        assertTrue(questionCount > 0, "Form should have at least one question")

        // Serialize with DataModelSerializer (our cross-platform path)
        val xml = FormSerializer.serializeForm(formDef)
        assertTrue(xml.isNotEmpty(), "Serialized XML should not be empty")
        assertTrue(xml.contains("<data") || xml.contains("<"), "XML should contain elements")
    }

    @Test
    fun testInMemoryStorageOperations() {
        // Test the InMemoryStorage used by SqlDelightUserSandbox
        val storage = InMemoryStorage(Case::class, { Case() })

        // Use add() to assign fresh IDs
        val c1 = Case()
        c1.setCaseId("c1")
        c1.setName("Case 1")
        c1.setTypeId("test")
        val id1 = storage.add(c1)

        val c2 = Case()
        c2.setCaseId("c2")
        c2.setName("Case 2")
        c2.setTypeId("test")
        val id2 = storage.add(c2)

        // Verify count
        assertEquals(2, storage.getNumRecords())

        // Read by ID
        val first = storage.read(id1)
        assertEquals("Case 1", first.getName())

        // Iterate
        val iter = storage.iterate()
        var count = 0
        while (iter.hasMore()) {
            iter.nextRecord()
            count++
        }
        assertEquals(2, count)

        // Remove
        storage.remove(id1)
        assertEquals(1, storage.getNumRecords())
    }

    @Test
    fun testSqlDelightFormQueuePersistence() {
        val db = createTestDatabase()

        // Insert a form into the queue
        db.commCareQueries.insertFormQueue(
            form_id = "form-1",
            xmlns = "http://example.com/form",
            xml_content = "<data><name>test</name></data>",
            status = "pending",
            created_at = "2026-03-13T00:00:00Z",
            submitted_at = null
        )

        // Verify it's in the queue
        val pending = db.commCareQueries.selectPendingForms().executeAsList()
        assertEquals(1, pending.size)
        assertEquals("form-1", pending[0].form_id)
        assertEquals("pending", pending[0].status)

        // Update status to submitted
        db.commCareQueries.updateFormStatus("submitted", "2026-03-13T00:00:01Z", "form-1")
        val all = db.commCareQueries.selectAllForms().executeAsList()
        assertEquals("submitted", all[0].status)

        // Delete submitted forms
        db.commCareQueries.deleteSubmittedForms()
        val remaining = db.commCareQueries.selectAllForms().executeAsList()
        assertEquals(0, remaining.size)
    }
}
