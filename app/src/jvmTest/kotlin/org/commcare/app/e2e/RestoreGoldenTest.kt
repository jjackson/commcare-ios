package org.commcare.app.e2e

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.parse.ParseUtils
import org.javarosa.core.io.createByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Golden test that parses the real restore XML from basic_app/restore.xml
 * (a production-format OTA restore payload) into a SqlDelightUserSandbox and
 * verifies that the sync token, user registration, cases, and fixtures are
 * all extracted correctly.
 *
 * This catches parser regressions against real production-format data that
 * synthetic benchmarks may miss.
 */
class RestoreGoldenTest {

    private fun createSandbox(): SqlDelightUserSandbox {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        val db = CommCareDatabase(driver)
        return SqlDelightUserSandbox(db)
    }

    private fun loadRestoreXml(): ByteArray {
        // The restore XML lives in commcare-core's test resources (not on app's
        // test classpath). Read it from the file system relative to the app/ dir.
        val file = File("../commcare-core/src/test/resources/basic_app/restore.xml")
        if (!file.exists()) {
            throw AssertionError(
                "Could not find basic_app/restore.xml at ${file.absolutePath}. " +
                    "Ensure commcare-core subtree is present."
            )
        }
        return file.readBytes()
    }

    private fun parseRestore(): SqlDelightUserSandbox {
        val sandbox = createSandbox()
        val bytes = loadRestoreXml()
        ParseUtils.parseIntoSandbox(
            createByteArrayInputStream(bytes),
            sandbox,
            false
        )
        return sandbox
    }

    @Test
    fun testSyncTokenExtracted() {
        val sandbox = parseRestore()
        assertEquals(
            "9f7c4e0a3a8ca4e2ecf54444fb7e70cf",
            sandbox.syncToken,
            "Sync token should match restore_id in XML"
        )
    }

    @Test
    fun testUserRegistrationParsed() {
        val sandbox = parseRestore()
        val userStorage = sandbox.getUserStorage()
        assertTrue(
            userStorage.getNumRecords() > 0,
            "User storage should contain at least one user after restore"
        )
        val user = userStorage.read(0)
        assertNotNull(user, "Should be able to read the registered user")
        assertEquals("vl1", user.getUsername(), "Username should be 'vl1'")
        assertEquals(
            "7afceb0259b2866be17b3632392f8a4b",
            user.getUniqueId(),
            "User UUID should match registration block"
        )
    }

    @Test
    fun testCasesPopulated() {
        val sandbox = parseRestore()
        val caseStorage = sandbox.getCaseStorage()
        // The restore XML contains 36 case blocks
        assertEquals(
            36,
            caseStorage.getNumRecords(),
            "All 36 cases from restore should be parsed"
        )
    }

    @Test
    fun testCaseTypesPresent() {
        val sandbox = parseRestore()
        val caseStorage = sandbox.getCaseStorage()

        // Verify specific case types from the restore
        val parentCases = caseStorage.getIDsForValue("case-type", "parent")
        assertTrue(parentCases.size >= 3, "Should have at least 3 'parent' cases")

        val coverageCases = caseStorage.getIDsForValue("case-type", "coverage_basic")
        assertTrue(coverageCases.size >= 4, "Should have at least 4 'coverage_basic' cases")

        val priCases = caseStorage.getIDsForValue("case-type", "pri")
        assertTrue(priCases.size >= 3, "Should have at least 3 'pri' cases")

        val graphCases = caseStorage.getIDsForValue("case-type", "graphing_test_cases")
        assertTrue(graphCases.size >= 3, "Should have at least 3 'graphing_test_cases' cases")
    }

    @Test
    fun testSpecificCaseData() {
        val sandbox = parseRestore()
        val caseStorage = sandbox.getCaseStorage()

        // Find the case with ID "519cdd66-3d83-4cbd-a4e5-c24252b72e76" (first parent case)
        val ids = caseStorage.getIDsForValue("case-id", "519cdd66-3d83-4cbd-a4e5-c24252b72e76")
        assertTrue(ids.isNotEmpty(), "Should find the first parent case by ID")

        val parentCase = caseStorage.read(ids.first())
        assertNotNull(parentCase, "Should read the parent case")
        assertEquals("P", parentCase.getName(), "Case name should be 'P'")
        assertEquals("parent", parentCase.getTypeId(), "Case type should be 'parent'")
    }

    @Test
    fun testClosedCaseHandled() {
        val sandbox = parseRestore()
        val caseStorage = sandbox.getCaseStorage()

        // Case ad3739bb-d645-455e-9c60-1d385b9d6ba3 has a <close/> element
        val ids = caseStorage.getIDsForValue("case-id", "ad3739bb-d645-455e-9c60-1d385b9d6ba3")
        assertTrue(ids.isNotEmpty(), "Should find the closed case")

        val closedCase = caseStorage.read(ids.first())
        assertNotNull(closedCase, "Closed case should still be in storage")
        assertTrue(closedCase.isClosed(), "Case should be marked as closed")
    }

    @Test
    fun testFixturesParsed() {
        val sandbox = parseRestore()

        // The restore contains user-groups fixture (non-indexed) and locations (indexed)
        val userFixtures = sandbox.getUserFixtureStorage()
        assertTrue(
            userFixtures.getNumRecords() > 0,
            "User fixture storage should have at least one fixture (user-groups)"
        )
    }

    @Test
    fun testCaseWithIndexParsed() {
        val sandbox = parseRestore()
        val caseStorage = sandbox.getCaseStorage()

        // Case bb6c7418 (child "Aadhya") has a parent index to case 9d7ad332
        val ids = caseStorage.getIDsForValue("case-id", "bb6c7418-cd2b-4a6b-941c-4913e308ac75")
        assertTrue(ids.isNotEmpty(), "Should find child case Aadhya")

        val childCase = caseStorage.read(ids.first())
        assertNotNull(childCase, "Should read child case")
        assertEquals("Aadhya", childCase.getName(), "Child case name should be 'Aadhya'")

        // Verify the parent index exists
        val indices = childCase.getIndices()
        val parentIndex = indices.find { it.getName() == "parent" }
        assertNotNull(parentIndex, "Child case should have a 'parent' index")
        assertEquals(
            "9d7ad332-c28c-4ba3-a02d-eaf297ce23a1",
            parentIndex.getTarget(),
            "Parent index should point to the correct parent case ID"
        )
    }

    @Test
    fun testFullRestoreRoundTrip() {
        // End-to-end: parse, verify count, verify sync token, verify a case property
        val sandbox = parseRestore()

        assertEquals("9f7c4e0a3a8ca4e2ecf54444fb7e70cf", sandbox.syncToken)
        assertEquals(36, sandbox.getCaseStorage().getNumRecords())
        assertTrue(sandbox.getUserStorage().getNumRecords() > 0)

        // Verify a case property value: case 96676704's "text" property should be "hj"
        val ids = sandbox.getCaseStorage()
            .getIDsForValue("case-id", "96676704-7938-45e2-adb1-623a467f6c32")
        assertTrue(ids.isNotEmpty(), "Should find case 96676704")
        val caseObj = sandbox.getCaseStorage().read(ids.first())
        assertEquals("hj", caseObj.getPropertyString("text"), "Case property 'text' should be 'hj'")
    }
}
