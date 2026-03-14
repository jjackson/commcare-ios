package org.javarosa.xpath.expr.test

import org.javarosa.xpath.expr.XPathJsonPropertyFunc
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the XPathJsonProperty
 *
 * @author rcostello
 */
class XPathJsonPropertyFuncTest {

    @Test
    fun getJsonProperty() {
        val testObj1 = """{"name":"Sam"}"""
        val testVal1 = XPathJsonPropertyFunc.getJsonProperty(testObj1, "name")
        val testVal2 = XPathJsonPropertyFunc.getJsonProperty(testObj1, "city")
        assertEquals(testVal1, "Sam")
        assertEquals(testVal2, "")

        val testObj2 = "{city: New York}"
        val testVal3 = XPathJsonPropertyFunc.getJsonProperty(testObj2, "city")
        val testVal4 = XPathJsonPropertyFunc.getJsonProperty(testObj2, "state")
        assertEquals(testVal3, "New York")
        assertEquals(testVal4, "")

        val testInvalidObj = """{"name"}: "Sam"}"""
        val testVal5 = XPathJsonPropertyFunc.getJsonProperty(testInvalidObj, "name")
        assertEquals(testVal5, "")

        val testEmptyStrObj = ""
        val testVal6 = XPathJsonPropertyFunc.getJsonProperty(testEmptyStrObj, "name")
        assertEquals(testVal6, "")
    }
}
