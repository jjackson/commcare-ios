package org.javarosa.xpath

import org.javarosa.xpath.expr.XPathNumericLiteral
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.expr.XPathStringLiteral
import org.javarosa.xpath.parser.XPathSyntaxException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for XPath parsing via XPathParseTool.
 */
class XPathParseCrossPlatformTest {

    @Test
    fun testParseSimplePath() {
        val expr = XPathParseTool.parseXPath("/data/name")
        assertNotNull(expr)
        assertTrue(expr is XPathPathExpr)
    }

    @Test
    fun testParseStringLiteral() {
        val expr = XPathParseTool.parseXPath("'hello'")
        assertNotNull(expr)
        assertTrue(expr is XPathStringLiteral)
        assertEquals("hello", (expr as XPathStringLiteral).s)
    }

    @Test
    fun testParseNumericLiteral() {
        val expr = XPathParseTool.parseXPath("42")
        assertNotNull(expr)
        assertTrue(expr is XPathNumericLiteral)
        assertEquals(42.0, (expr as XPathNumericLiteral).d)
    }

    @Test
    fun testParseNestedPath() {
        val expr = XPathParseTool.parseXPath("/data/group/question")
        assertNotNull(expr)
        assertTrue(expr is XPathPathExpr)
        val pathExpr = expr as XPathPathExpr
        assertEquals(3, pathExpr.steps.size)
    }

    @Test
    fun testParseRelativePath() {
        val expr = XPathParseTool.parseXPath("../sibling")
        assertNotNull(expr)
        assertTrue(expr is XPathPathExpr)
    }

    @Test
    fun testParsePredicatePath() {
        val expr = XPathParseTool.parseXPath("/data/item[position() = 1]")
        assertNotNull(expr)
        assertTrue(expr is XPathPathExpr)
    }

    @Test
    fun testParseFunctionCall() {
        val expr = XPathParseTool.parseXPath("concat('a', 'b')")
        assertNotNull(expr)
    }

    @Test
    fun testParseArithmetic() {
        val expr = XPathParseTool.parseXPath("1 + 2")
        assertNotNull(expr)
    }

    @Test
    fun testParseComparison() {
        val expr = XPathParseTool.parseXPath("/data/age > 18")
        assertNotNull(expr)
    }

    @Test
    fun testParseCurrentRef() {
        val expr = XPathParseTool.parseXPath(".")
        assertNotNull(expr)
        assertTrue(expr is XPathPathExpr)
    }

    @Test
    fun testParseSyntaxError() {
        assertFailsWith<XPathSyntaxException> {
            XPathParseTool.parseXPath("[invalid xpath")
        }
    }

    @Test
    fun testParseEmptyStringLiteral() {
        val expr = XPathParseTool.parseXPath("''")
        assertNotNull(expr)
        assertTrue(expr is XPathStringLiteral)
        assertEquals("", (expr as XPathStringLiteral).s)
    }
}
