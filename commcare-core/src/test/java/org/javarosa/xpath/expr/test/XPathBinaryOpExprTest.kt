package org.javarosa.xpath.expr.test

import org.javarosa.xpath.expr.XPathArithExpr
import org.javarosa.xpath.expr.XPathBoolExpr
import org.javarosa.xpath.expr.XPathCmpExpr
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathNumericLiteral
import org.javarosa.xpath.expr.XPathStringLiteral
import org.javarosa.xpath.expr.XPathUnionExpr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class XPathBinaryOpExprTest {
    /**
     * Extensive checks of binary op expression equality logic, because we all
     * know 'a > b' isn't the same as 'a = b', but we don't always write code
     * that knows that...
     */
    @Test
    fun equalityForDifferentBinaryOps() {
        val leftStringExpr = XPathStringLiteral("left side")
        val zero = XPathNumericLiteral(0.0)

        // Setup expressions to test equality over.
        // Note: these binary expressions make semantic sense
        val additionExpr = XPathArithExpr(XPathArithExpr.ADD, leftStringExpr, zero)
        val additionExprClone = XPathArithExpr(XPathArithExpr.ADD, leftStringExpr, zero)
        val subtractExpr = XPathArithExpr(XPathArithExpr.SUBTRACT, leftStringExpr, zero)

        val andExpr = XPathBoolExpr(XPathBoolExpr.AND, leftStringExpr, zero)
        val andExprClone = XPathBoolExpr(XPathBoolExpr.AND, leftStringExpr, zero)
        val orExpr = XPathBoolExpr(XPathBoolExpr.OR, leftStringExpr, zero)

        val lessThanExpr = XPathCmpExpr(XPathCmpExpr.LT, leftStringExpr, zero)
        val greaterThanExpr = XPathCmpExpr(XPathCmpExpr.GT, leftStringExpr, zero)

        val eqExpr = XPathEqExpr(XPathEqExpr.EQ, leftStringExpr, zero)
        val neqExpr = XPathEqExpr(XPathEqExpr.NEQ, leftStringExpr, zero)

        val union = XPathUnionExpr(leftStringExpr, zero)
        val differentUnion = XPathUnionExpr(zero, zero)

        // basic equality tests over same subclass
        assertEquals("Same + expression reference is equal", additionExpr, additionExpr)
        assertEquals("Same + expression is equal", additionExpr, additionExprClone)
        assertNotEquals("+ not equal to  -", additionExpr, subtractExpr)
        assertEquals("Same && expression reference is equal", andExpr, andExpr)
        assertEquals("Same && expression is equal", andExpr, andExprClone)
        assertNotEquals("&& not equal to ||", andExpr, orExpr)
        assertEquals("Same < expression is equal", lessThanExpr, lessThanExpr)
        assertNotEquals("< not equal to  >", lessThanExpr, greaterThanExpr)
        assertEquals("Same == expression is equal", eqExpr, eqExpr)
        assertNotEquals("== not equal to !=", eqExpr, neqExpr)

        // make sure different binary expressions with same op code aren't equal
        assertNotEquals("+ not equal to &&", additionExpr, andExpr)
        assertNotEquals("+ not equal to <", additionExpr, lessThanExpr)
        assertNotEquals("+ not equal to ==", additionExpr, eqExpr)
        assertNotEquals("- not equal to ||", subtractExpr, orExpr)
        assertNotEquals("- not equal to >", subtractExpr, greaterThanExpr)
        assertNotEquals("- not equal to !=", subtractExpr, neqExpr)

        // make sure union equality, which doesn't have an op code, works
        assertEquals("same union instance is equal to itself", union, union)
        assertNotEquals(union, differentUnion)
        assertNotEquals("+ not equal to union", additionExpr, union)
    }
}
