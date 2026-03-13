package org.commcare.cases

import org.commcare.cases.model.Case
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for the Case model — core data object for CommCare case management.
 */
class CaseCrossPlatformTest {

    @Test
    fun testCreateEmptyCase() {
        val c = Case()
        assertNull(c.getTypeId())
        assertNull(c.getName())
    }

    @Test
    fun testCreateNamedCase() {
        val c = Case("John Doe", "patient")
        assertEquals("John Doe", c.getName())
        assertEquals("patient", c.getTypeId())
    }

    @Test
    fun testSetCaseId() {
        val c = Case("Test", "type")
        c.setCaseId("abc-123")
        assertEquals("abc-123", c.getCaseId())
    }

    @Test
    fun testCaseClosedFlag() {
        val c = Case("Test", "type")
        assertFalse(c.isClosed())

        c.setClosed(true)
        assertTrue(c.isClosed())

        c.setClosed(false)
        assertFalse(c.isClosed())
    }

    @Test
    fun testSetAndGetProperty() {
        val c = Case("Test", "type")
        c.setProperty("color", "blue")
        assertEquals("blue", c.getPropertyString("color"))
    }

    @Test
    fun testMultipleProperties() {
        val c = Case("Test", "type")
        c.setProperty("first_name", "Alice")
        c.setProperty("last_name", "Smith")
        c.setProperty("age", "30")

        assertEquals("Alice", c.getPropertyString("first_name"))
        assertEquals("Smith", c.getPropertyString("last_name"))
        assertEquals("30", c.getPropertyString("age"))
    }

    @Test
    fun testOverwriteProperty() {
        val c = Case("Test", "type")
        c.setProperty("status", "open")
        assertEquals("open", c.getPropertyString("status"))

        c.setProperty("status", "closed")
        assertEquals("closed", c.getPropertyString("status"))
    }

    @Test
    fun testCaseIdMetaProperty() {
        val c = Case("Test", "type")
        c.setCaseId("xyz-789")

        // "case-id" is a special meta property
        assertEquals("xyz-789", c.getProperty("case-id"))
    }

    @Test
    fun testCaseTypeMetaData() {
        val c = Case("Test", "patient")
        assertEquals("patient", c.getMetaData("case-type"))
    }

    @Test
    fun testCaseStatusMetaData() {
        val c = Case("Test", "type")
        assertEquals("open", c.getMetaData("case-status"))

        c.setClosed(true)
        assertEquals("closed", c.getMetaData("case-status"))
    }
}
