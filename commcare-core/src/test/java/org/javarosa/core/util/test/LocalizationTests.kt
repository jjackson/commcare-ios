package org.javarosa.core.util.test

import org.javarosa.core.services.locale.LocalizationUtils
import org.javarosa.core.util.OrderedHashtable
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTests {

    @Test
    fun testParseValue() {
        val result1 = LocalizationUtils.parseValue("1. One \\n2. Two \\n3. Three")
        val result2 = LocalizationUtils.parseValue("\\# Start")

        assertEquals("Parsed newlines correctly", "1. One \n2. Two \n3. Three", result1)
        assertEquals("Parsed hash correctly", "# Start", result2)
    }

    @Test
    fun testParseAndAdd() {
        val testTable = OrderedHashtable<String, String>()

        LocalizationUtils.parseAndAdd(testTable, "string.1=this line should be cutoff here# this is bad if present", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.2=this line should be cutoff here after the space # this is bad is present", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.3=this line should all be here#", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.4=this line should all be here #", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.5=this line should be all here \\# including this \\# and this", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.6=this line should be here \\# and this# not this", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.7=this line should be here \\# and this# not this \\# not this either", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.8=we have a hash \\# and a newline \\n", 0)
        LocalizationUtils.parseAndAdd(testTable, "string.9=this line should all be here with the hash \\#", 0)
        // make sure doesn't crash
        LocalizationUtils.parseAndAdd(testTable, "# this is the whole line", 0)

        assertEquals("Line 1 failed: ${testTable["string.1"]}", "this line should be cutoff here", testTable["string.1"])
        assertEquals("Line 2 failed: ${testTable["string.2"]}", "this line should be cutoff here after the space ", testTable["string.2"])
        assertEquals("Line 3 failed: ${testTable["string.3"]}", "this line should all be here", testTable["string.3"])
        assertEquals("Line 4 failed: ${testTable["string.4"]}", "this line should all be here ", testTable["string.4"])
        assertEquals("Line 5 failed: ${testTable["string.5"]}", "this line should be all here # including this # and this", testTable["string.5"])
        assertEquals("Line 6 failed: ${testTable["string.6"]}", "this line should be here # and this", testTable["string.6"])
        assertEquals("Line 7 failed: ${testTable["string.7"]}", "this line should be here # and this", testTable["string.7"])
        assertEquals("Line 8 failed: ${testTable["string.8"]}", "we have a hash # and a newline \n", testTable["string.8"])
        assertEquals("Line 9 failed: ${testTable["string.9"]}", "this line should all be here with the hash #", testTable["string.9"])
    }
}
