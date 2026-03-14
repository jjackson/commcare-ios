package org.commcare.cases

import org.commcare.cases.model.Case
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for the Case data model.
 * Runs on both JVM and iOS targets.
 */
class CaseModelTest {

    @Test
    fun createCaseWithNameAndType() {
        val case = Case("John Doe", "patient")
        assertEquals("John Doe", case.getName())
        assertEquals("patient", case.getTypeId())
    }

    @Test
    fun setCaseId() {
        val case = Case("Test", "test_type")
        case.setCaseId("abc-123")
        assertEquals("abc-123", case.getCaseId())
    }

    @Test
    fun defaultCaseIsNotClosed() {
        val case = Case("Test", "test_type")
        assertFalse(case.isClosed())
    }

    @Test
    fun setClosed() {
        val case = Case("Test", "test_type")
        case.setClosed(true)
        assertTrue(case.isClosed())
    }

    @Test
    fun setAndGetName() {
        val case = Case("Original", "test_type")
        case.setName("Updated")
        assertEquals("Updated", case.getName())
    }

    @Test
    fun setAndGetTypeId() {
        val case = Case("Test", "original_type")
        case.setTypeId("new_type")
        assertEquals("new_type", case.getTypeId())
    }

    @Test
    fun setAndGetCustomProperty() {
        val case = Case("Test", "test_type")
        case.setProperty("color", "blue")
        assertEquals("blue", case.getPropertyString("color"))
    }

    @Test
    fun setMultipleProperties() {
        val case = Case("Test", "test_type")
        case.setProperty("color", "red")
        case.setProperty("size", "large")
        case.setProperty("weight", "10")

        assertEquals("red", case.getPropertyString("color"))
        assertEquals("large", case.getPropertyString("size"))
        assertEquals("10", case.getPropertyString("weight"))
    }

    @Test
    fun getPropertyReturnsNullForMissing() {
        val case = Case("Test", "test_type")
        assertNull(case.getProperty("nonexistent"))
    }

    @Test
    fun getPropertyReturnsNullForMissingKey() {
        val case = Case("Test", "test_type")
        // getProperty returns null for keys that haven't been set
        val result = case.getProperty("nonexistent")
        assertNull(result)
    }

    @Test
    fun overwriteProperty() {
        val case = Case("Test", "test_type")
        case.setProperty("status", "open")
        assertEquals("open", case.getPropertyString("status"))

        case.setProperty("status", "closed")
        assertEquals("closed", case.getPropertyString("status"))
    }

    @Test
    fun caseIdPropertyAccess() {
        val case = Case("Test", "test_type")
        case.setCaseId("case-456")
        // getProperty("case-id") returns the case ID
        assertEquals("case-456", case.getProperty("case-id"))
    }

    @Test
    fun setAndGetUserId() {
        val case = Case("Test", "test_type")
        case.setUserId("user-789")
        assertEquals("user-789", case.getUserId())
    }

    @Test
    fun setAndGetExternalId() {
        val case = Case("Test", "test_type")
        case.setExternalId("ext-001")
        assertEquals("ext-001", case.getExternalId())
    }

    @Test
    fun defaultIdIsNegativeOne() {
        val case = Case("Test", "test_type")
        assertEquals(-1, case.getID())
    }

    @Test
    fun setAndGetRecordId() {
        val case = Case("Test", "test_type")
        case.setID(42)
        assertEquals(42, case.getID())
    }

    @Test
    fun dateOpenedIsSet() {
        val case = Case("Test", "test_type")
        assertNotNull(case.getDateOpened())
    }

    @Test
    fun setNullPropertyIsIgnored() {
        val case = Case("Test", "test_type")
        case.setProperty("key", "value")
        case.setProperty("key", null)
        // setProperty ignores null values, so original remains
        assertEquals("value", case.getPropertyString("key"))
    }

    @Test
    fun getPropertiesMap() {
        val case = Case("Test", "test_type")
        case.setProperty("a", "1")
        case.setProperty("b", "2")
        val props = case.getProperties()
        assertTrue(props.containsKey("a"))
        assertTrue(props.containsKey("b"))
    }

    @Test
    fun restorableType() {
        val case = Case("Test", "test_type")
        assertEquals("case", case.getRestorableType())
    }
}
