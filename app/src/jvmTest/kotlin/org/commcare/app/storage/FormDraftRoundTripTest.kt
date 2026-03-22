package org.commcare.app.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for form_records table round-trip persistence.
 *
 * Verifies that form drafts (status = 'incomplete') can be inserted,
 * queried, updated, and deleted through the SQLDelight-generated queries.
 */
class FormDraftRoundTripTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    @Test
    fun testInsertAndRetrieve_incompleteFormRecord() {
        val db = createTestDatabase()
        val queries = db.commCareQueries

        queries.insertFormRecord(
            form_id = "draft-001",
            xmlns = "http://openrosa.org/formdesigner/test-form",
            form_name = "Patient Registration",
            status = "incomplete",
            serialized_instance = "<data><name>John</name><age>30</age></data>",
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:00:00Z"
        )

        val record = queries.selectFormRecordById("draft-001").executeAsOneOrNull()
        assertNotNull(record, "Inserted form record should be retrievable")
        assertEquals("draft-001", record.form_id)
        assertEquals("http://openrosa.org/formdesigner/test-form", record.xmlns)
        assertEquals("Patient Registration", record.form_name)
        assertEquals("incomplete", record.status)
        assertEquals("<data><name>John</name><age>30</age></data>", record.serialized_instance)
        assertEquals("2026-03-21T10:00:00Z", record.created_at)
        assertEquals("2026-03-21T10:00:00Z", record.updated_at)
    }

    @Test
    fun testSelectIncompleteFormRecords_filtersCorrectly() {
        val db = createTestDatabase()
        val queries = db.commCareQueries

        // Insert one incomplete and one complete record
        queries.insertFormRecord(
            form_id = "draft-001",
            xmlns = "http://example.com/form1",
            form_name = "Draft Form",
            status = "incomplete",
            serialized_instance = "<data/>",
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:00:00Z"
        )
        queries.insertFormRecord(
            form_id = "complete-001",
            xmlns = "http://example.com/form2",
            form_name = "Completed Form",
            status = "complete",
            serialized_instance = "<data><done>yes</done></data>",
            created_at = "2026-03-21T09:00:00Z",
            updated_at = "2026-03-21T11:00:00Z"
        )

        val incomplete = queries.selectIncompleteFormRecords().executeAsList()
        assertEquals(1, incomplete.size, "Should return only incomplete records")
        assertEquals("draft-001", incomplete[0].form_id)

        val complete = queries.selectCompleteFormRecords().executeAsList()
        assertEquals(1, complete.size, "Should return only complete records")
        assertEquals("complete-001", complete[0].form_id)
    }

    @Test
    fun testUpdateFormRecordStatus() {
        val db = createTestDatabase()
        val queries = db.commCareQueries

        queries.insertFormRecord(
            form_id = "draft-002",
            xmlns = "http://example.com/form",
            form_name = "Evolving Form",
            status = "incomplete",
            serialized_instance = "<data/>",
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:00:00Z"
        )

        // Transition from incomplete to complete
        queries.updateFormRecordStatus(
            status = "complete",
            updated_at = "2026-03-21T12:00:00Z",
            form_id = "draft-002"
        )

        val record = queries.selectFormRecordById("draft-002").executeAsOneOrNull()
        assertNotNull(record)
        assertEquals("complete", record.status)
        assertEquals("2026-03-21T12:00:00Z", record.updated_at)
        // created_at should be unchanged
        assertEquals("2026-03-21T10:00:00Z", record.created_at)
    }

    @Test
    fun testDeleteFormRecord() {
        val db = createTestDatabase()
        val queries = db.commCareQueries

        queries.insertFormRecord(
            form_id = "to-delete",
            xmlns = "http://example.com/form",
            form_name = "Disposable Form",
            status = "incomplete",
            serialized_instance = "<data/>",
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:00:00Z"
        )

        assertNotNull(queries.selectFormRecordById("to-delete").executeAsOneOrNull())

        queries.deleteFormRecord("to-delete")
        assertNull(
            queries.selectFormRecordById("to-delete").executeAsOneOrNull(),
            "Deleted form record should not be retrievable"
        )
    }

    @Test
    fun testInsertOrReplace_updatesExisting() {
        val db = createTestDatabase()
        val queries = db.commCareQueries

        // Insert initial record
        queries.insertFormRecord(
            form_id = "draft-003",
            xmlns = "http://example.com/form",
            form_name = "Original Name",
            status = "incomplete",
            serialized_instance = "<data><v>1</v></data>",
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:00:00Z"
        )

        // Re-insert with same ID (INSERT OR REPLACE)
        queries.insertFormRecord(
            form_id = "draft-003",
            xmlns = "http://example.com/form",
            form_name = "Updated Name",
            status = "incomplete",
            serialized_instance = "<data><v>2</v></data>",
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:30:00Z"
        )

        val records = queries.selectIncompleteFormRecords().executeAsList()
        assertEquals(1, records.size, "INSERT OR REPLACE should not create duplicates")
        assertEquals("Updated Name", records[0].form_name)
        assertEquals("<data><v>2</v></data>", records[0].serialized_instance)
    }

    @Test
    fun testLargeSerializedInstance() {
        val db = createTestDatabase()
        val queries = db.commCareQueries

        // Simulate a large form instance (~10KB)
        val largeXml = buildString {
            append("<data>")
            repeat(200) { i ->
                append("<field_$i>Value for field number $i with some additional text to make it larger</field_$i>")
            }
            append("</data>")
        }

        queries.insertFormRecord(
            form_id = "large-draft",
            xmlns = "http://example.com/large-form",
            form_name = "Large Form",
            status = "incomplete",
            serialized_instance = largeXml,
            created_at = "2026-03-21T10:00:00Z",
            updated_at = "2026-03-21T10:00:00Z"
        )

        val record = queries.selectFormRecordById("large-draft").executeAsOneOrNull()
        assertNotNull(record)
        assertEquals(largeXml, record.serialized_instance, "Large XML content should round-trip intact")
    }
}
