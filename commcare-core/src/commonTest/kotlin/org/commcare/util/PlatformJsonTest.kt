package org.commcare.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformJsonTest {

    @Test
    fun testJsonGetStringBasic() {
        val json = """{"name": "CommCare", "version": "2.53"}"""
        assertEquals("CommCare", jsonGetString(json, "name"))
        assertEquals("2.53", jsonGetString(json, "version"))
    }

    @Test
    fun testJsonGetStringMissingKey() {
        val json = """{"name": "CommCare"}"""
        assertNull(jsonGetString(json, "missing"))
    }

    @Test
    fun testJsonGetStringEmptyObject() {
        assertNull(jsonGetString("{}", "key"))
    }

    @Test
    fun testJsonGetStringInvalidJson() {
        assertNull(jsonGetString("not json", "key"))
    }

    @Test
    fun testJsonArrayToStringListBasic() {
        val json = """["alpha", "beta", "gamma"]"""
        assertEquals(listOf("alpha", "beta", "gamma"), jsonArrayToStringList(json))
    }

    @Test
    fun testJsonArrayToStringListEmpty() {
        assertEquals(emptyList(), jsonArrayToStringList("[]"))
    }

    @Test
    fun testJsonArrayToStringListInvalid() {
        assertEquals(emptyList(), jsonArrayToStringList("not an array"))
    }
}
