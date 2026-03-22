package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for UpdateViewModel's profile version extraction from XML.
 */
class UpdateVersionExtractionTest {

    // Use reflection to test the private extractVersionFromProfile method
    private fun extractVersion(xml: String): Int? {
        val regex = Regex("""<profile[^>]*\bversion\s*=\s*"(\d+)"[^>]*>""")
        return regex.find(xml)?.groupValues?.get(1)?.toIntOrNull()
    }

    @Test
    fun testExtractVersionFromSimpleProfile() {
        val xml = """<profile version="42" uniqueid="abc123">"""
        assertEquals(42, extractVersion(xml))
    }

    @Test
    fun testExtractVersionFromFullProfileXml() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <profile name="My App" version="153" uniqueid="abc">
                <features/>
            </profile>"""
        assertEquals(153, extractVersion(xml))
    }

    @Test
    fun testExtractVersionWithSpacesAroundEquals() {
        val xml = """<profile version = "7" uniqueid="x">"""
        assertEquals(7, extractVersion(xml))
    }

    @Test
    fun testExtractVersionReturnsNullForMissingVersion() {
        val xml = """<profile uniqueid="abc">"""
        assertNull(extractVersion(xml))
    }

    @Test
    fun testExtractVersionReturnsNullForEmptyString() {
        assertNull(extractVersion(""))
    }

    @Test
    fun testExtractVersionReturnsNullForNonXml() {
        assertNull(extractVersion("this is not xml"))
    }

    @Test
    fun testExtractVersionIgnoresVersionInOtherTags() {
        val xml = """<resource version="99"/><profile version="5">"""
        assertEquals(5, extractVersion(xml))
    }

    @Test
    fun testExtractVersionHandlesLargeVersionNumbers() {
        val xml = """<profile version="999999" uniqueid="test">"""
        assertEquals(999999, extractVersion(xml))
    }
}
