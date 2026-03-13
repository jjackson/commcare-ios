package org.commcare.app.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.cases.model.Case
import org.javarosa.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlDelightUserSandboxTest {

    private fun createTestSandbox(): SqlDelightUserSandbox {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        val db = CommCareDatabase(driver)
        return SqlDelightUserSandbox(db)
    }

    @Test
    fun testCaseStorageWriteAndRead() {
        val sandbox = createTestSandbox()
        val storage = sandbox.getCaseStorage()

        val case1 = Case("patient-123", "patient")
        case1.setID(0)
        case1.setName("Test Patient")
        storage.write(case1)

        assertEquals(1, storage.getNumRecords())
        val retrieved = storage.read(0)
        assertNotNull(retrieved)
    }

    @Test
    fun testCaseStorageIterate() {
        val sandbox = createTestSandbox()
        val storage = sandbox.getCaseStorage()

        val case1 = Case("case-1", "patient")
        case1.setName("Patient 1")
        storage.add(case1)

        val case2 = Case("case-2", "patient")
        case2.setName("Patient 2")
        storage.add(case2)

        assertEquals(2, storage.getNumRecords())

        var count = 0
        val iter = storage.iterate()
        while (iter.hasMore()) {
            iter.nextRecord()
            count++
        }
        assertEquals(2, count)
    }

    @Test
    fun testCaseStorageMetaIndex() {
        val sandbox = createTestSandbox()
        val storage = sandbox.getCaseStorage()

        val case1 = Case("case-1", "patient")
        case1.setName("Patient 1")
        storage.add(case1)

        val case2 = Case("case-2", "household")
        case2.setName("Household 1")
        storage.add(case2)

        val patientIds = storage.getIDsForValue("case-type", "patient")
        assertEquals(1, patientIds.size)

        val householdIds = storage.getIDsForValue("case-type", "household")
        assertEquals(1, householdIds.size)
    }

    @Test
    fun testUserStorage() {
        val sandbox = createTestSandbox()
        val userStorage = sandbox.getUserStorage()
        assertNotNull(userStorage)
        assertEquals(0, userStorage.getNumRecords())
    }

    @Test
    fun testLedgerStorage() {
        val sandbox = createTestSandbox()
        val ledgerStorage = sandbox.getLedgerStorage()
        assertNotNull(ledgerStorage)
        assertEquals(0, ledgerStorage.getNumRecords())
    }

    @Test
    fun testLoggedInUser() {
        val sandbox = createTestSandbox()
        val user = User()
        sandbox.setLoggedInUser(user)
        assertNotNull(sandbox.getLoggedInUser())
    }

    @Test
    fun testSyncToken() {
        val sandbox = createTestSandbox()
        sandbox.syncToken = "abc-token-123"
        assertEquals("abc-token-123", sandbox.syncToken)
    }

    @Test
    fun testFormQueuePersistence() {
        val sandbox = createTestSandbox()
        sandbox.db.commCareQueries.insertFormQueue(
            form_id = "form-1",
            xmlns = "http://example.com/form",
            xml_content = "<data><name>test</name></data>",
            status = "pending",
            created_at = "2026-03-13",
            submitted_at = null
        )

        val pending = sandbox.db.commCareQueries.selectPendingForms().executeAsList()
        assertEquals(1, pending.size)
        assertEquals("form-1", pending[0].form_id)
    }
}
