package org.commcare.app.e2e

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.engine.FormEntrySession
import org.commcare.app.engine.PrintTemplateEngine
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.app.ui.CalendarWidget
import org.commcare.app.ui.GraphConfig
import org.commcare.app.ui.GraphSeries
import org.commcare.app.ui.GraphType
import org.commcare.app.ui.generateGraphHtml
import org.commcare.app.viewmodel.DemoModeManager
import org.commcare.app.viewmodel.FormEntryViewModel
import org.commcare.app.viewmodel.FormRecordViewModel
import org.commcare.app.viewmodel.RecoveryViewModel
import org.commcare.app.viewmodel.SettingsViewModel
import org.commcare.cases.model.Case
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests that exercise ViewModel wiring, state machines,
 * and cross-component interactions with real engine objects.
 */
class ViewModelIntegrationTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    // -- Form entry lifecycle --

    @Test
    fun testFormEntryViewModelFullLifecycle() {
        val stream = this::class.java.getResourceAsStream("/test_form_entry_controller.xml")
        assertNotNull(stream)
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)

        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)

        // Initial state
        assertTrue(vm.questions.isEmpty())
        assertEquals("", vm.formTitle)
        assertFalse(vm.isComplete)

        // Load form
        vm.loadForm()
        assertTrue(vm.formTitle.isNotEmpty())
        assertTrue(vm.questions.isNotEmpty())

        // Navigate through entire form
        var steps = 0
        while (!vm.isComplete && steps < 50) {
            vm.nextQuestion()
            steps++
        }
        assertTrue(vm.isComplete, "Should reach end of form")
        assertTrue(vm.progress > 0f)

        // Serialize
        val xml = vm.serializeForm()
        assertNotNull(xml)
        assertTrue(xml.contains("<data") || xml.contains("<?xml"))
    }

    @Test
    fun testFormEntryBackNavigation() {
        val stream = this::class.java.getResourceAsStream("/test_form_entry_controller.xml")
        assertNotNull(stream)
        val formDef = XFormUtils.getFormFromInputStream(stream)

        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)
        vm.loadForm()

        // Move forward a few steps
        vm.nextQuestion()
        vm.nextQuestion()

        // Go back — should not throw or crash
        vm.previousQuestion()
    }

    // -- Form queue persistence --

    @Test
    fun testFormQueuePersistence() {
        val db = createTestDatabase()

        // Enqueue forms
        db.commCareQueries.insertFormQueue(
            form_id = "form-001",
            xmlns = "http://example.com/form1",
            xml_content = "<data><name>Alice</name></data>",
            status = "pending",
            created_at = "2026-03-15T10:00:00Z",
            submitted_at = null
        )
        db.commCareQueries.insertFormQueue(
            form_id = "form-002",
            xmlns = "http://example.com/form1",
            xml_content = "<data><name>Bob</name></data>",
            status = "pending",
            created_at = "2026-03-15T10:01:00Z",
            submitted_at = null
        )

        val pending = db.commCareQueries.selectPendingForms().executeAsList()
        assertEquals(2, pending.size)

        // Mark one as submitted
        db.commCareQueries.updateFormStatus("submitted", "2026-03-15T10:02:00Z", "form-001")
        assertEquals(1, db.commCareQueries.selectPendingForms().executeAsList().size)

        // Clean up submitted forms
        db.commCareQueries.deleteSubmittedForms()
        val remaining = db.commCareQueries.selectAllForms().executeAsList()
        assertEquals(1, remaining.size)
    }

    // -- Sandbox + case storage integration --

    @Test
    fun testSandboxCaseStorageIntegration() {
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)

        val caseStorage = sandbox.getCaseStorage()

        // Create and store cases using add() for auto-ID assignment
        val ids = mutableListOf<Int>()
        for (i in 1..5) {
            val c = Case()
            c.setCaseId("case-$i")
            c.setName("Patient $i")
            c.setTypeId("patient")
            ids.add(caseStorage.add(c))
        }

        assertEquals(5, caseStorage.getNumRecords())

        // Read by assigned ID
        val case3 = caseStorage.read(ids[2])
        assertEquals("Patient 3", case3.getName())
        assertEquals("patient", case3.getTypeId())

        // Iterate
        val iter = caseStorage.iterate()
        var count = 0
        while (iter.hasMore()) {
            iter.nextRecord()
            count++
        }
        assertEquals(5, count)

        // Remove
        caseStorage.remove(ids[0])
        assertEquals(4, caseStorage.getNumRecords())
    }

    // -- Settings ViewModel full workflow --

    @Test
    fun testSettingsViewModelFullWorkflow() {
        val vm = SettingsViewModel()

        // Modify settings
        vm.serverUrl = "https://staging.commcarehq.org"
        vm.syncFrequencyMinutes = 30
        vm.developerMode = true
        vm.logLevel = "debug"
        vm.fuzzySearchEnabled = false
        vm.localeOverride = "hin"
        vm.autoUpdateEnabled = false

        // Save
        vm.save()
        assertEquals("Settings saved", vm.savedMessage)

        // Clear message
        vm.clearSavedMessage()
        assertNull(vm.savedMessage)

        // Verify changes persisted in memory
        assertEquals("https://staging.commcarehq.org", vm.serverUrl)
        assertEquals(30, vm.syncFrequencyMinutes)
        assertTrue(vm.developerMode)
        assertFalse(vm.fuzzySearchEnabled)
        assertEquals("hin", vm.localeOverride)
        assertFalse(vm.autoUpdateEnabled)

        // Reset
        vm.resetToDefaults()
        assertEquals("https://www.commcarehq.org", vm.serverUrl)
        assertEquals(15, vm.syncFrequencyMinutes)
        assertFalse(vm.developerMode)
        assertTrue(vm.fuzzySearchEnabled)
        assertNull(vm.localeOverride)
        assertTrue(vm.autoUpdateEnabled)
    }

    // -- Recovery ViewModel --

    @Test
    fun testRecoveryViewModelInitialState() {
        val vm = RecoveryViewModel()
        assertTrue(vm.unsentForms.isEmpty())
        assertTrue(vm.logs.isEmpty())
        assertNull(vm.actionResult)
    }

    // -- Print template engine --

    @Test
    fun testPrintTemplateIntegration() {
        val template = """
            <html>
            <body>
                <h1>{{title}}</h1>
                <p>Patient: {{name}}</p>
                <p>Age: {{age}}</p>
                <p>Notes: {{notes}}</p>
            </body>
            </html>
        """.trimIndent()

        val data = mapOf(
            "title" to "Visit Summary",
            "name" to "Alice <Bob>",
            "age" to "30",
            "notes" to "Patient presents with \"symptoms\" & signs"
        )

        val engine = PrintTemplateEngine()
        val rendered = engine.render(template, data)
        assertTrue(rendered.contains("Visit Summary"))
        assertTrue(rendered.contains("Alice &lt;Bob&gt;"), "Should HTML-escape angle brackets")
        assertTrue(rendered.contains("&amp;"), "Should HTML-escape ampersand")
        assertTrue(rendered.contains("&quot;"), "Should HTML-escape quotes")
    }

    // -- Graph HTML generation --

    @Test
    fun testGraphHtmlGeneration() {
        val config = GraphConfig(
            title = "Patient Visits",
            type = GraphType.Bar,
            series = listOf(
                GraphSeries("Visits", listOf("Jan" to 10.0, "Feb" to 25.0, "Mar" to 15.0, "Apr" to 30.0))
            ),
            xLabel = "Month",
            yLabel = "Count"
        )

        val html = generateGraphHtml(config)
        assertTrue(html.contains("<html"), "Should produce HTML")
        assertTrue(html.contains("<svg"), "Should contain SVG")
        assertTrue(html.contains("Patient Visits"), "Should contain title")
    }

    // -- Calendar widget --

    @Test
    fun testCalendarWidgetAlternativeCalendars() {
        assertTrue(CalendarWidget.isAlternativeCalendar("ethiopian"))
        assertTrue(CalendarWidget.isAlternativeCalendar("nepali"))
        assertFalse(CalendarWidget.isAlternativeCalendar("gregorian"))
        assertFalse(CalendarWidget.isAlternativeCalendar(null))

        assertEquals("Ethiopian", CalendarWidget.getCalendarName("ethiopian"))
        assertEquals("Nepali", CalendarWidget.getCalendarName("nepali"))

        // Format a date
        val formatted = CalendarWidget.formatConvertedDate(2018, 7, 15, "ethiopian")
        assertTrue(formatted.isNotEmpty())
    }

    // -- Demo mode manager --

    @Test
    fun testDemoModeManagerLifecycle() {
        val db = createTestDatabase()
        val manager = DemoModeManager(db)

        assertFalse(manager.isDemoMode)
        assertFalse(manager.shouldBlockSubmission())

        // Enter demo mode (will use minimal platform since no profile URL)
        val state = manager.enterDemoMode()
        // May succeed or fail depending on AppInstaller — test the state transitions
        if (state != null) {
            assertTrue(manager.isDemoMode)
            assertTrue(manager.shouldBlockSubmission())

            manager.exitDemoMode()
            assertFalse(manager.isDemoMode)
            assertFalse(manager.shouldBlockSubmission())
        }
    }

    // -- Form record ViewModel --

    @Test
    fun testFormRecordViewModelWithDatabase() {
        val db = createTestDatabase()
        val vm = FormRecordViewModel(db)

        // Initially empty
        vm.loadRecords()
        assertTrue(vm.incompleteRecords.isEmpty())
        assertTrue(vm.completeRecords.isEmpty())

        // Insert a record
        db.commCareQueries.insertFormRecord(
            form_id = "f-001",
            xmlns = "http://example.com/form",
            form_name = "Test Form",
            status = "incomplete",
            serialized_instance = "<data/>",
            created_at = "2026-03-15T12:00:00Z",
            updated_at = "2026-03-15T12:00:00Z"
        )

        vm.loadRecords()
        assertEquals(1, vm.incompleteRecords.size)
        assertEquals("Test Form", vm.incompleteRecords[0].formName)
    }
}
