package org.javarosa.xpath.test

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.services.locale.TableLocaleSource
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.IExprDataType
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.XPathUnhandledException
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathNumericLiteral
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test
import java.io.UnsupportedEncodingException
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class XPathEvalTest {

    companion object {
        const val DOUBLE_TOLERANCE = 1.0e-12
    }

    private fun testEval(expr: String, model: FormInstance?, ec: EvaluationContext?, expected: Any) {
        val exceptionExpected = expected is XPathException || expected is XPathSyntaxException
        val evalContext = ec ?: EvaluationContext(model)

        val xpe: XPathExpression?
        try {
            xpe = XPathParseTool.parseXPath(expr)
        } catch (e: XPathSyntaxException) {
            assertExceptionExpected(exceptionExpected, expected, e)
            return
        }

        if (xpe == null) {
            fail("Null expression or syntax error $expr")
            return
        }

        try {
            val result = FunctionUtils.unpack(xpe.eval(model, evalContext))

            if (exceptionExpected) {
                fail("Expected exception, expression : $expr")
            } else if (result is Double && expected is Double) {
                if (Math.abs(result - expected) > DOUBLE_TOLERANCE) {
                    fail("Doubles outside of tolerance [$result,$expected ]")
                } else if (java.lang.Double.isNaN(result) && !java.lang.Double.isNaN(expected)) {
                    fail("Result was NaN when not expected")
                } else if (java.lang.Double.isNaN(expected) && !java.lang.Double.isNaN(result)) {
                    fail("Result was supposed to be NaN, but got $result")
                }
            } else if (expected != result) {
                // Allow minor floating point precision differences across JDK implementations
                if (expected is String && result is String && approximateGeopointMatch(expected, result)) {
                    // close enough
                } else {
                    fail("Expected $expected, got $result (expr = '$expr')")
                }
            }
        } catch (xpex: XPathException) {
            assertExceptionExpected(exceptionExpected, expected, xpex)
        }
    }

    private fun approximateGeopointMatch(a: String, b: String): Boolean {
        val partsA = a.split(" ")
        val partsB = b.split(" ")
        if (partsA.size != partsB.size) return false
        return partsA.zip(partsB).all { (sa, sb) ->
            val da = sa.toDoubleOrNull() ?: return false
            val db = sb.toDoubleOrNull() ?: return false
            kotlin.math.abs(da - db) < 1e-10
        }
    }

    @Throws(Exception::class)
    private fun evalExpr(expr: String, model: FormInstance?, ec: EvaluationContext?): Any? {
        val evalContext = ec ?: EvaluationContext(model)
        val xpe = XPathParseTool.parseXPath(expr)
        if (xpe == null) {
            fail("Null expression or syntax error $expr")
            return null
        }
        return FunctionUtils.unpack(xpe.eval(model, evalContext))
    }

    private fun assertExceptionExpected(exceptionExpected: Boolean, expected: Any?, xpex: Exception) {
        if (!exceptionExpected) {
            xpex.printStackTrace()
            fail("Did not expect ${xpex.javaClass} exception")
        } else if (xpex.javaClass != expected!!.javaClass) {
            xpex.printStackTrace()
            fail("Expected ${expected.javaClass} exception type but was provided ${xpex.javaClass}")
        }
    }

    @Test
    fun testTypeCoercion() {
        val str = FunctionUtils.InferType("notadouble")
        Assert.assertTrue("'notadouble' coerced to the wrong type, ${str.javaClass}", str is String)

        val d = FunctionUtils.InferType("5.0")
        Assert.assertTrue("'5.0' coerced to the wrong type, ${d.javaClass}", d is Double)
    }

    @Test
    fun sortTests() {
        // simple sort
        testEval("sort('commcare is the best tool ever', false())", null, null, "tool the is ever commcare best")

        // sort 2nd list by 1st
        testEval("sort-by('2222 5555 9999 1111', 'd b c a', true())", null, null, "1111 5555 9999 2222")
        testEval("sort-by('a b c d e f', '4 2 1 5 3 2', false())", null, null, "d a e f b c")
        testEval("sort-by('c c z f z f', '4 2 1 5 3 2', true())", null, null, "z c f z c f")

        // ascending bool not explicitly included
        testEval("sort-by('a b c d e f', '4 2 1 5 3 2')", null, null, "c b f e a d")
        testEval("sort('commcare is the best tool ever')", null, null, "best commcare ever is the tool")

        // uneven list sizes
        testEval("sort-by('a b c', '4 2 5 1', true())", null, null, XPathTypeMismatchException())
    }

    @Test
    fun testRegex() {
        val ec = getFunctionHandlers()
        testEval("regex('12345','[0-9]+')", null, ec, true)
        testEval("regex('12345','[')", null, ec, XPathException())
        testEval("regex('aaaabfooaaabgarplyaaabwackyb', 'a*b')", null, null, true)
        testEval("regex('photo', 'a*b')", null, null, false)
        testEval("regex('Is this right?', 'is')", null, null, true)
        testEval("regex('Is this right?', '^is')", null, null, false)
        testEval("regex('Is this right?', '^Is this right?$')", null, null, false)
        testEval("regex('Is this right?', '^Is this right\\?$')", null, null, true)
        testEval("regex('Dollar sign\ndoes not match newlines', 'sign$')", null, null, false)
        testEval("regex('Dollar sign\ndoes not match newlines', 'newlines$')", null, null, true)
        testEval("regex('cocotero', 'cocotero')", null, null, true)
        testEval("regex('cocotero', 'te')", null, null, true)
        testEval(String.format("regex('%s', '%s')",
                "1.1.1.1",
                "^([1,2]{0,1}[0-9]{1,2}.){3}[1,2]{0,1}[0-9]{1,2}$"
        ), null, null, true)
        testEval(String.format("regex('%ss', '%s')",
                "Andrew.weston-lewis@state,co.us",
                "^([a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+@([a-zA-Z0-9-]+\\.)+[a-zA-Z0-9-]+$"
        ), null, null, false)
    }

    @Test
    fun doTests() {
        System.setProperty("user.timezone", "UTC")
        val ec = getFunctionHandlers()

        val instance = createTestInstance()

        /* unsupporteds */
        testEval("/union | /expr", null, null, XPathUnsupportedException())
        testEval("/descendant::blah", null, null, XPathUnsupportedException())
        testEval("/cant//support", null, null, XPathUnsupportedException())
        testEval("/text()", null, null, XPathUnsupportedException())
        testEval("/namespace:*", null, null, XPathUnsupportedException())
        testEval("(filter-expr)[5]", instance, null, XPathUnsupportedException())
        testEval("(filter-expr)/data", instance, null, XPathUnsupportedException())
        /* numeric literals */
        testEval("5", null, null, 5.0)
        testEval("555555.555", null, null, 555555.555)
        testEval(".000555", null, null, 0.000555)
        testEval("0", null, null, 0.0)
        testEval("-5", null, null, -5.0)
        testEval("-0", null, null, -0.0)
        testEval("1230000000000000000000", null, null, 1.23e21)
        testEval("0.00000000000000000123", null, null, 1.23e-18)
        /* string literals */
        testEval("''", null, null, "")
        testEval("'\"'", null, null, "\"")
        testEval("\"test string\"", null, null, "test string")
        testEval("'   '", null, null, "   ")
        /* base type conversion functions */
        testEval("true()", null, null, true)
        testEval("false()", null, null, false)
        testEval("boolean(true())", null, null, true)
        testEval("boolean(false())", null, null, false)
        testEval("boolean(1)", null, null, true)
        testEval("boolean(-1)", null, null, true)
        testEval("boolean(0.0001)", null, null, true)
        testEval("boolean(0)", null, null, false)
        testEval("boolean(-0)", null, null, false)
        testEval("boolean(number('NaN'))", null, null, false)
        testEval("boolean(1 div 0)", null, null, true)
        testEval("boolean(-1 div 0)", null, null, true)
        testEval("boolean('')", null, null, false)
        testEval("boolean('asdf')", null, null, true)
        testEval("boolean('  ')", null, null, true)
        testEval("boolean('false')", null, null, true)
        testEval("boolean(date('2000-01-01'))", null, null, true)
        testEval("boolean(convertible())", null, ec, true)
        testEval("boolean(inconvertible())", null, ec, XPathTypeMismatchException())
        testEval("number(true())", null, null, 1.0)
        testEval("number(false())", null, null, 0.0)
        testEval("number('100')", null, null, 100.0)
        testEval("number('100.001')", null, null, 100.001)
        testEval("number('.1001')", null, null, 0.1001)
        testEval("number('1230000000000000000000')", null, null, 1.23e21)
        testEval("number('0.00000000000000000123')", null, null, 1.23e-18)
        testEval("number('0')", null, null, 0.0)
        testEval("number('-0')", null, null, -0.0)
        testEval("number(' -12345.6789  ')", null, null, -12345.6789)
        testEval("number('NaN')", null, null, Double.NaN)
        testEval("number('not a number')", null, null, Double.NaN)
        testEval("number('- 17')", null, null, Double.NaN)
        testEval("number('  ')", null, null, Double.NaN)
        testEval("number('')", null, null, Double.NaN)
        testEval("number('Infinity')", null, null, Double.NaN)
        testEval("number('1.1e6')", null, null, Double.NaN)
        testEval("number('34.56.7')", null, null, Double.NaN)
        testEval("number(10)", null, null, 10.0)
        testEval("number(0)", null, null, 0.0)
        testEval("number(-0)", null, null, -0.0)
        testEval("number(-123.5)", null, null, -123.5)
        testEval("number(number('NaN'))", null, null, Double.NaN)
        testEval("number(1 div 0)", null, null, Double.POSITIVE_INFINITY)
        testEval("number(-1 div 0)", null, null, Double.NEGATIVE_INFINITY)
        testEval("number(date('1970-01-01'))", null, null, 0.0)
        testEval("number(date('1970-01-02'))", null, null, 1.0)
        testEval("number(date('1969-12-31'))", null, null, -1.0)
        testEval("number(date('2008-09-05'))", null, null, 14127.0)
        testEval("number(date('1941-12-07'))", null, null, -10252.0)
        testEval("number('1970-01-01')", null, null, 0.0)
        testEval("number('1970-01-02')", null, null, 1.0)
        testEval("number('1969-12-31')", null, null, -1.0)
        testEval("number('2008-09-05')", null, null, 14127.0)
        testEval("number('1941-12-07')", null, null, -10252.0)
        testEval("number('1970-01')", null, null, Double.NaN)
        testEval("number('-1970-01-02')", null, null, Double.NaN)
        testEval("number('12-31')", null, null, Double.NaN)
        testEval("number('2016-13-13')", null, null, Double.NaN)
        testEval("number('2017-01-45')", null, null, Double.NaN)

        testEval("number(convertible())", null, ec, 5.0)
        testEval("number(inconvertible())", null, ec, XPathTypeMismatchException())
        testEval("string(true())", null, null, "true")
        testEval("string(false())", null, null, "false")
        testEval("string(number('NaN'))", null, null, "NaN")
        testEval("string(1 div 0)", null, null, "Infinity")
        testEval("string(-1 div 0)", null, null, "-Infinity")
        testEval("string(0)", null, null, "0")
        testEval("string(-0)", null, null, "0")
        testEval("string(123456.0000)", null, null, "123456")
        testEval("string(-123456)", null, null, "-123456")
        testEval("string(1)", null, null, "1")
        testEval("string(-1)", null, null, "-1")
        testEval("string(.557586)", null, null, "0.557586")
        //broken: testEval("string(1230000000000000000000)", null, null, "1230000000000000000000")
        //broken: testEval("string(0.00000000000000000123)", null, null, "0.00000000000000000123")
        testEval("string('')", null, null, "")
        testEval("string('  ')", null, null, "  ")
        testEval("string('a string')", null, null, "a string")
        testEval("string(date('1989-11-09'))", null, null, "1989-11-09")
        testEval("string(convertible())", null, ec, "hi")
        testEval("string(inconvertible())", null, ec, XPathTypeMismatchException())
        testEval("substr('hello',0)", null, null, "hello")
        testEval("substr('hello',0,5)", null, null, "hello")
        testEval("substr('hello',1)", null, null, "ello")
        testEval("substr('hello',1,5)", null, null, "ello")
        testEval("substr('hello',1,4)", null, null, "ell")
        testEval("substr('hello',-2)", null, null, "lo")
        testEval("substr('hello',0,-1)", null, null, "hell")
        testEval("substr('',0,1)", null, null, "")
        testEval("substr('hello',0,8)", null, null, "")
        testEval("date('2000-01-01')", null, null, DateUtils.getDate(2000, 1, 1)!!)
        testEval("date('1945-04-26')", null, null, DateUtils.getDate(1945, 4, 26)!!)
        testEval("date('1996-02-29')", null, null, DateUtils.getDate(1996, 2, 29)!!)
        testEval("date('1983-09-31')", null, null, XPathTypeMismatchException())
        testEval("date('not a date')", null, null, XPathTypeMismatchException())
        testEval("date(0)", null, null, DateUtils.getDate(1970, 1, 1)!!)
        testEval("date(6.5)", null, null, DateUtils.getDate(1970, 1, 7)!!)
        testEval("date(1)", null, null, DateUtils.getDate(1970, 1, 2)!!)
        testEval("date(-1)", null, null, DateUtils.getDate(1969, 12, 31)!!)
        testEval("date(14127)", null, null, DateUtils.getDate(2008, 9, 5)!!)
        testEval("date(-10252)", null, null, DateUtils.getDate(1941, 12, 7)!!)
        testEval("date(date('1989-11-09'))", null, null, DateUtils.getDate(1989, 11, 9)!!)
        testEval("date(true())", null, null, XPathTypeMismatchException())
        testEval("date(convertible())", null, ec, XPathTypeMismatchException())
        testEval("format-date-for-calendar('', 'ethiopian')", null, null, "")
        testEval("format-date-for-calendar(date('1970-01-01'), 'neverland')", null, null, XPathUnsupportedException())

        configureLocaleForCalendar()
        testEval("format-date-for-calendar('2017-07-15', 'ethiopian', '%Y-%m-%d')", null, null, "2009-11-08")
        testEval("format-date-for-calendar('2017-07-15', 'nepali', '%Y-%m-%d')", null, null, "2074-03-31")

        //note: there are lots of time and timezone-like issues with dates that should be tested (particularly DST changes),
        //    but it's just too hard and client-dependent, so not doing it now
        //  basically:
        //        dates cannot reliably be compared/used across time zones (an issue with the code)
        //        any time-of-day or DST should be ignored when comparing/using a date (an issue with testing)
        /* other built-in functions */
        testEval("not(true())", null, null, false)
        testEval("not(false())", null, null, true)
        testEval("not('')", null, null, true)
        testEval("boolean-from-string('true')", null, null, true)
        testEval("boolean-from-string('false')", null, null, false)
        testEval("boolean-from-string('whatever')", null, null, false)
        testEval("boolean-from-string('1')", null, null, true)
        testEval("boolean-from-string('0')", null, null, false)
        testEval("boolean-from-string(1)", null, null, true)
        testEval("boolean-from-string(1.0)", null, null, true)
        testEval("boolean-from-string(1.0001)", null, null, false)
        testEval("boolean-from-string(true())", null, null, true)
        testEval("if(true())", null, null, XPathSyntaxException())
        testEval("if(true(), 5, 'abc')", null, null, 5.0)
        testEval("if(false(), 5, 'abc')", null, null, "abc")
        testEval("if(6 > 7, 5, 'abc')", null, null, "abc")
        testEval("if('', 5, 'abc')", null, null, "abc")
        testEval("selected('apple baby crimson', 'apple')", null, null, true)
        testEval("selected('apple baby crimson', 'baby')", null, null, true)
        testEval("selected('apple baby crimson', 'crimson')", null, null, true)
        testEval("selected('apple baby crimson', '  baby  ')", null, null, true)
        testEval("selected('apple baby crimson', 'babby')", null, null, false)
        testEval("selected('apple baby crimson', 'bab')", null, null, false)
        testEval("selected('apple', 'apple')", null, null, true)
        testEval("selected('apple', 'ovoid')", null, null, false)
        testEval("selected('', 'apple')", null, null, false)
        /* operators */

        testEval("min(5.5, 0.5)", null, null, 0.5)
        testEval("min(5.5)", null, null, 5.5)
        testEval("min(-2,-3)", null, null, -3.0)
        testEval("min(2,-3)", null, null, -3.0)
        testEval("date(min(date('2012-02-05'), date('2012-01-01')))", null, null, DateUtils.parseDate("2012-01-01")!!)

        testEval("max(5.5, 0.5)", null, null, 5.5)
        testEval("max(0.5)", null, null, 0.5)
        testEval("max(-2,-3)", null, null, -2.0)
        testEval("max(2,-3)", null, null, 2.0)
        testEval("date(max(date('2012-02-05'), date('2012-01-01')))", null, null, DateUtils.parseDate("2012-02-05")!!)

        // Test that taking the min or max of date-strings works, but still fails properly for
        // numeric strings that are not dates
        testEval("min('2012-02-05', '2012-01-01', '2012-04-20')", null, null,
                DateUtils.daysSinceEpoch(DateUtils.parseDate("2012-01-01")!!).toDouble())
        testEval("max('2012-02-05', '2012-01-01', '2012-04-20')", null, null,
                DateUtils.daysSinceEpoch(DateUtils.parseDate("2012-04-20")!!).toDouble())
        testEval("max('-1-02-05', '2012-01-01', '2012-04-20')", null, null, Double.NaN)
        testEval("max('02-05', '2012-01-01', '2012-04-20')", null, null, Double.NaN)
        testEval("max('2012-14-05', '2012-01-01', '2012-04-20')", null, null, Double.NaN)

        testEval("5.5 + 5.5", null, null, 11.0)
        testEval("0 + 0", null, null, 0.0)
        testEval("6.1 - 7.8", null, null, -1.7)
        testEval("-3 + 4", null, null, 1.0)
        testEval("3 + -4", null, null, -1.0)
        testEval("1 - 2 - 3", null, null, -4.0)
        testEval("1 - (2 - 3)", null, null, 2.0)
        testEval("-(8*5)", null, null, -40.0)
        testEval("-'19'", null, null, -19.0)
        testEval("1.1 * -1.1", null, null, -1.21)
        testEval("-10 div -4", null, null, 2.5)
        testEval("2 * 3 div 8 * 2", null, null, 1.5)
        testEval("3 + 3 * 3", null, null, 12.0)
        testEval("1 div 0", null, null, Double.POSITIVE_INFINITY)
        testEval("-1 div 0", null, null, Double.NEGATIVE_INFINITY)
        testEval("0 div 0", null, null, Double.NaN)
        testEval("3.1 mod 3.1", null, null, 0.0)
        testEval("5 mod 3.1", null, null, 1.9)
        testEval("2 mod 3.1", null, null, 2.0)
        testEval("0 mod 3.1", null, null, 0.0)
        testEval("5 mod -3", null, null, 2.0)
        testEval("-5 mod 3", null, null, -2.0)
        testEval("-5 mod -3", null, null, -2.0)
        testEval("5 mod 0", null, null, Double.NaN)
        testEval("5 * (6 + 7)", null, null, 65.0)
        testEval("'123' * '456'", null, null, 56088.0)
        testEval("true() + 8", null, null, 9.0)
        testEval("date('2008-09-08') - date('1983-10-06')", null, null, 9104.0)
        testEval("true() and true()", null, null, true)
        testEval("true() and false()", null, null, false)
        testEval("false() and false()", null, null, false)
        testEval("true() or true()", null, null, true)
        testEval("true() or false()", null, null, true)
        testEval("false() or false()", null, null, false)
        testEval("true() or true() and false()", null, null, true)
        testEval("(true() or true()) and false()", null, null, false)
        testEval("true() or date('')", null, null, true) //short-circuiting
        testEval("false() and date('')", null, null, false) //short-circuiting
        testEval("'' or 17", null, null, true)
        testEval("false() or 0 + 2", null, null, true)
        testEval("(false() or 0) + 2", null, null, 2.0)
        testEval("4 < 5", null, null, true)
        testEval("5 < 5", null, null, false)
        testEval("6 < 5", null, null, false)
        testEval("4 <= 5", null, null, true)
        testEval("5 <= 5", null, null, true)
        testEval("6 <= 5", null, null, false)
        testEval("4 > 5", null, null, false)
        testEval("5 > 5", null, null, false)
        testEval("6 > 5", null, null, true)
        testEval("4 >= 5", null, null, false)
        testEval("5 >= 5", null, null, true)
        testEval("6 >= 5", null, null, true)
        testEval("-3 > -6", null, null, true)
        testEval("true() > 0.9999", null, null, true)
        testEval("'-17' > '-172'", null, null, true) //no string comparison: converted to number
        testEval("'abc' < 'abcd'", null, null, false) //no string comparison: converted to NaN
        testEval("date('2001-12-26') > date('2001-12-25')", null, null, true)
        testEval("date('1969-07-20') < date('1969-07-21')", null, null, true)

        testEval("double(date('2004-05-01T05:00:00')) > double(date('2004-05-01T02:00:00'))", null, null, true)
        testEval("not(double(date('2004-05-01T05:00:00')) < double(date('2004-05-01T02:00:00')))", null, null, true)
        testEval("not(double(date('2004-05-01T05:00:00')) = double(date('2004-05-01T02:00:00')))", null, null, true)
        testEval("double(date('2004-05-01T04:00:00')) < double(date('2004-05-01T016:00:00'))", null, null, true)

        testEval("(double(date('2004-05-01T07:00:00')) - double(date('2004-05-01T03:00:00'))) < (6.0 div 24) ", null, null, true)
        testEval("(double(date('2004-05-01T07:00:00')) - double(date('2004-05-01T00:30:00'))) > (6.0 div 24) ", null, null, true)
        testEval("(double(date('2004-05-03T07:00:00')) - double(date('2004-05-01T03:00:00'))) > (6.0 div 24) ", null, null, true)

        testEval("abs(-3.5)", null, null, 3.5)
        testEval("abs(2)", null, null, 2.0)
        testEval("floor(-4.8)", null, null, -5.0)
        testEval("floor(100.2)", null, null, 100.0)
        testEval("ceiling(-0.5)", null, null, 0.0)
        testEval("ceiling(10.4)", null, null, 11.0)
        testEval("round(1.5)", null, null, 2.0)
        testEval("round(-1.5)", null, null, -1.0)
        testEval("round(1.455)", null, null, 1.0)

        testEval("log(${Math.E})", null, null, 1.0)
        testEval("log(1)", null, null, 0.0)
        testEval("log10(100)", null, null, 2.0)
        testEval("log10(1)", null, null, 0.0)

        testEval("pow(2, 2)", null, null, 4.0)
        testEval("pow(2, 0)", null, null, 1.0)
        testEval("pow(0, 4)", null, null, 0.0)
        testEval("pow(2.5, 2)", null, null, 6.25)
        testEval("pow(0.5, 2)", null, null, .25)

        testEval("pow(-1, 2)", null, null, 1.0)
        testEval("pow(-1, 3)", null, null, -1.0)
        testEval("sin(0)", null, null, 0.0)
        testEval("cos(0)", null, null, 1.0)
        testEval("tan(0)", null, null, 0.0)
        testEval("asin(0)", null, null, 0.0)
        testEval("acos(1)", null, null, 0.0)
        testEval("atan(0)", null, null, 0.0)
        testEval("atan2(0, 0)", null, null, 0.0)
        testEval("sqrt(4)", null, null, 2.0)
        testEval("exp(1)", null, null, Math.E)
        testEval("pi()", null, null, Math.PI)

        //So raising things to decimal powers is.... very hard
        //to evaluated exactly due to double floating point
        //precision. We'll try for things with clean answers
        //testEval("pow(4, 0.5)", null, null, 2.0, .001)
        //testEval("pow(16, 0.25)", null, null, 2.0, .001)
        //CTS: We're going to skip trying to do any sort of hackery workaround
        //for this for now and go with "Integer powers only"

        testEval("false() and false() < true()", null, null, false)
        testEval("(false() and false()) < true()", null, null, true)
        testEval("6 < 7 - 4", null, null, false)
        testEval("(6 < 7) - 4", null, null, -3.0)
        testEval("3 < 4 < 5", null, null, true)
        testEval("3 < (4 < 5)", null, null, false)
        testEval("true() = true()", null, null, true)
        testEval("true() = false()", null, null, false)
        testEval("true() != true()", null, null, false)
        testEval("true() != false()", null, null, true)
        testEval("3 = 3", null, null, true)
        testEval("3 = 4", null, null, false)
        testEval("3 != 3", null, null, false)
        testEval("3 != 4", null, null, true)
        testEval("6.1 - 7.8 = -1.7", null, null, true) //handle floating point rounding
        testEval("'abc' = 'abc'", null, null, true)
        testEval("'abc' = 'def'", null, null, false)
        testEval("'abc' != 'abc'", null, null, false)
        testEval("'abc' != 'def'", null, null, true)
        testEval("'' = ''", null, null, true)
        testEval("true() = 17", null, null, true)
        testEval("0 = false()", null, null, true)
        testEval("true() = 'true'", null, null, true)
        testEval("17 = '17.0000000'", null, null, true)
        testEval("'0017.' = 17", null, null, true)
        testEval("'017.' = '17.000'", null, null, false)
        testEval("date('2004-05-01') = date('2004-05-01')", null, null, true)
        testEval("true() != date('1999-09-09')", null, null, false)
        testEval("false() and true() != true()", null, null, false)
        testEval("(false() and true()) != true()", null, null, true)
        testEval("-3 < 3 = 6 >= 6", null, null, true)
        /* functions, including custom function handlers */
        testEval("weighted-checklist(5)", null, null, XPathArityException())
        testEval("weighted-checklist(5, 5, 5)", null, null, XPathArityException())
        testEval("substr('hello')", null, null, XPathArityException())
        testEval("join()", null, null, XPathArityException())
        testEval("substring-before()", null, null, XPathArityException())
        testEval("substring-after()", null, null, XPathArityException())
        testEval("string-length('123')", null, null, 3.0)
        testEval("join(',', '1', '2')", null, null, "1,2")
        testEval("join-chunked('-', 3, 'AA', 'BBB', 'C')", null, null, "AAB-BBC")
        testEval("join-chunked('-', 3, 'AA', 'BBB', 'CC')", null, null, "AAB-BBC-C")
        testEval("join-chunked('-', 3, 'AA')", null, null, "AA")
        testEval("join-chunked('-', 3, 'AAA')", null, null, "AAA")
        testEval("depend()", null, null, XPathArityException())
        testEval("depend('1', '2')", null, null, "1")
        testEval("uuid('1', '2')", null, null, XPathArityException())
        testEval("max()", null, null, XPathArityException())
        testEval("min()", null, null, XPathArityException())
        testEval("true(5)", null, null, XPathArityException())
        testEval("number()", null, null, XPathArityException())
        testEval("string('too', 'many', 'args')", null, null, XPathArityException())
        testEval("not-a-function()", null, null, XPathUnhandledException())
        testEval("testfunc()", null, ec, true)
        testEval("add(3, 5)", null, ec, 8.0)
        testEval("add('17', '-14')", null, ec, 3.0)
        // proto not setup for 0 arguments. Note that Arity is a parse exception, so we expect this
        // to get wrapped
        testEval("proto()", null, ec, XPathTypeMismatchException())
        testEval("proto(5, 5)", null, ec, "[Double:5.0,Double:5.0]")
        testEval("proto(6)", null, ec, "[Double:6.0]")
        testEval("proto('asdf')", null, ec, "[Double:NaN]")
        testEval("proto('7', '7')", null, ec, "[Double:7.0,Double:7.0]") //note: args treated as doubles because
        //(double, double) prototype takes precedence and strings are convertible to doubles
        testEval("proto(1.1, 'asdf', true())", null, ec, "[Double:1.1,String:asdf,Boolean:true]")
        testEval("proto(false(), false(), false())", null, ec, "[Double:0.0,String:false,Boolean:false]")
        testEval("proto(1.1, 'asdf', inconvertible())", null, ec, XPathTypeMismatchException())

        // proto not setup for 4 arguments. Note that Arity is a parse exception, so we expect this
        // to get wrapped
        testEval("proto(1.1, 'asdf', true(), 16)", null, ec, XPathTypeMismatchException())

        testEval("position(1.1, 'asdf')", null, ec, XPathArityException())
        testEval("sum(1)", null, ec, XPathTypeMismatchException())

        testEval("raw()", null, ec, "[]")
        testEval("raw(5, 5)", null, ec, "[Double:5.0,Double:5.0]")
        testEval("raw('7', '7')", null, ec, "[String:7,String:7]")
        testEval("raw('1.1', 'asdf', 17)", null, ec, "[Double:1.1,String:asdf,Boolean:true]") //convertible to prototype
        testEval("raw(get-custom(false()), get-custom(true()))", null, ec, "[CustomType:,CustomSubType:]")
        testEval("concat()", null, ec, "")
        testEval("concat('a')", null, ec, "a")
        testEval("concat('a','b','')", null, ec, "ab")
        testEval("concat('ab','cde','','fgh',1,false(),'ijklmnop')", null, ec, "abcdefgh1falseijklmnop")
        testEval("check-types(55, '55', false(), '1999-09-09', get-custom(false()))", null, ec, true)
        testEval("check-types(55, '55', false(), '1999-09-09', get-custom(true()))", null, ec, true)
        testEval("upper-case('SimpLY')", null, null, "SIMPLY")
        testEval("lower-case('rEd')", null, null, "red")
        testEval("contains('', 'stuff')", null, null, false)
        testEval("contains('stuff', '')", null, null, true)
        testEval("contains('know', 'now')", null, null, true)
        testEval("contains('now', 'know')", null, null, false)
        testEval("starts-with('finish', 'fin')", null, null, true)
        testEval("starts-with('keep', '')", null, null, true)
        testEval("starts-with('why', 'y')", null, null, false)
        testEval("ends-with('elements', 'nts')", null, null, true)
        testEval("ends-with('elements', 'xenon')", null, null, false)
        testEval("translate('aBcdE', 'xyz', 'qrs')", null, null, "aBcdE")
        testEval("translate('bosco', 'bos', 'sfo')", null, null, "sfocf")
        testEval("translate('ramp', 'mapp', 'nbqr')", null, null, "rbnq")
        testEval("translate('yellow', 'low', 'or')", null, null, "yeoor")
        testEval("translate('bora bora', 'a', 'bc')", null, null, "borb borb")
        testEval("translate('squash me', 'aeiou ', '')", null, null, "sqshm")
        testEval("replace('aaaabfooaaabgarplyaaabwackyb', 'a*b', '-')", null, null, "-foo-garply-wacky-")
        testEval("replace('abbc', 'a(.*)c', '\$1')", null, null, "\$1")
        testEval("replace('aaabb', '[ab][ab][ab]', '')", null, null, "bb")
        testEval("replace('12345','[', '')", null, ec, XPathException())
        testEval("checklist('12345')", null, ec, XPathArityException())
        testEval("weighted-checklist('12345')", null, ec, XPathArityException())
        testEval("id-compress(0, 'CD','AB','ABCDE',1)", null, null, "AA")
        testEval("id-compress(9, 'CD','AB','ABCDE',1)", null, null, "BE")
        testEval("id-compress(10, 'CD','AB','ABCDE',1)", null, null, "DAA")

        testEval("id-compress(0, 'CD','','ABCDE',1)", null, ec, XPathException())
        testEval("id-compress(0, 'CD','CD','ABCDE',1)", null, ec, XPathException())

        testEval("checksum('verhoeff','41310785898')", null, null, "4")
        testEval("checksum('verhoeff','66671496237')", null, null, "3")
        testEval("checksum('verhoeff','*1310785898')", null, null, XPathUnsupportedException())
        testEval("checksum('verhoefffff','41310785898')", null, null, XPathUnsupportedException())

        //Variables
        val varContext = getVariableContext()
        testEval("\$var_float_five", null, varContext, 5.0)
        testEval("\$var_string_five", null, varContext, "five")
        testEval("\$var_int_five", null, varContext, 5.0)
        testEval("\$var_double_five", null, varContext, 5.0)
        //Polygon point
        testEval(
                "closest-point-on-polygon('27.176 78.041','27.174957 78.041309 27.174884 78.042574 27.175493 78.042661 27.175569 78.041383')",
                null, null, "27.175569 78.041383")  // Outside, near bottom-left vertex

        testEval(
                "closest-point-on-polygon('27.175 78.043','27.174957 78.041309 27.174884 78.042574 27.175493 78.042661 27.175569 78.041383')",
                null, null, "27.175046033868735 78.04259714760182")  // Near top-right edge

        testEval(
                "closest-point-on-polygon('27.175 78.042','27.174 78.041 27.174 78.043 27.176 78.043 27.176 78.041')",
                null, null, "27.1750000035729 78.04099999999998")  // Inside polygon

        testEval(
                "closest-point-on-polygon('27.177 78.042','27.174 78.040 27.174 78.044 27.176 78.044 27.176 78.040')",
                null, null, "27.17600001425841 78.04200000002879")  // Near top edge

        testEval(
                "closest-point-on-polygon('27.175 78.039','27.174 78.040 27.174 78.044 27.176 78.044 27.176 78.040')",
                null, null, "27.1750000035729 78.04")  // Left of polygon

        testEval(
                "closest-point-on-polygon('27.1755 78.045','27.174 78.040 27.174 78.044 27.176 78.044 27.176 78.040')",
                null, null, "27.175500003557083 78.044")  // Right side

        testEval(
                "closest-point-on-polygon('27.173 78.042','27.174 78.040 27.174 78.044 27.176 78.044 27.176 78.040')",
                null, null, "27.1740000142577 78.04199999997121")  // Bottom side

        //inside polygon
        testEval(
                "is-point-inside-polygon('27.204 78.0195','27.2043773 78.0186987 27.203509 78.0187201 27.2035281 78.0202758 27.2044155 78.0203027')",
                null, null, true)  // Inside

        testEval(
                "is-point-inside-polygon('27.2035 78.0205','27.2043773 78.0186987 27.203509 78.0187201 27.2035281 78.0202758 27.2044155 78.0203027')",
                null, null, false)  // Outside, near bottom-right

        testEval(
                "is-point-inside-polygon('27.204 78.018','27.2043773 78.0186987 27.203509 78.0187201 27.2035281 78.0202758 27.2044155 78.0203027')",
                null, null, false)  // Outside, far left

        testEval(
                "is-point-inside-polygon('27.203509 78.0187201','27.2043773 78.0186987 27.203509 78.0187201 27.2035281 78.0202758 27.2044155 78.0203027')",
                null, null, true)  // On vertex

        testEval(
                "is-point-inside-polygon('27.203509 78.0187201','27.2043773 78.0186987 27.203509 78.0187201 27.2035281 78.0202758 27.2044155 78.0203027')",
                null, null, true)  // On vertex again

        testEval(
                "closest-point-on-polygon('27.175 91.043','27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.175046033868725 91.04259714760182")

        testEval(
                "closest-point-on-polygon('27.175 91.043','27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.175046033868725 91.04259714760182")
        testEval(
                "closest-point-on-polygon('27.176 91.043', '27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.175493 91.042661")
        testEval(
                "closest-point-on-polygon('27.175 91.040', '27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.174957 91.041309")
        testEval(
                "closest-point-on-polygon('27.175 91.044', '27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.17515847322515 91.04261321034194")
        testEval(
                "closest-point-on-polygon('27.176 91.041', '27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.175569 91.041383")
        testEval(
                "closest-point-on-polygon('27.175 91.043', '27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, "27.175046033868725 91.04259714760182")
        testEval(
                "closest-point-on-polygon('27.176 91.043','91.041309 27.174957 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, XPathException())
        testEval(
                "closest-point-on-polygon('91.043 27.176','91.041309 27.174957 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, XPathException())
        testEval(
                "closest-point-on-polygon('91.043 27.176','27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, XPathException())

        testEval(
                "closest-point-on-polygon('27.176 182.043','27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, XPathException())

        testEval(
                "closest-point-on-polygon('27.176 -182.043','27.174957 91.041309 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, XPathException())

        testEval(
                "closest-point-on-polygon('27.176 91.043','27.174957 -184.056 27.174884 91.042574 27.175493 91.042661 27.175569 91.041383')",
                null, null, XPathException())
        //Test cases for large polygons ~150km
        testEval(
                "closest-point-on-polygon('27.28 91.32', '27.20 91.10 27.20 91.30 27.40 91.30 27.40 91.10')",
                null, null, "27.280001426949784 91.30000000000001"
        )
        testEval(
                "closest-point-on-polygon('27.35 91.30', '27.20 91.10 27.20 91.30 27.40 91.30 27.40 91.10')",
                null, null, "27.349999999999998 91.30000000000001"
        )

        //Attribute XPath References
        //testEval("/@blah", null, null, XPathUnsupportedException())
        //TODO: Need to test with model, probably in a different file

        val wildcardIndex = "index/*"
        val indexOne = "index/some_index"
        val indexTwo = "index/another_index"
        val exprWild = XPathReference.getPathExpr(wildcardIndex)
        val expr2 = XPathReference.getPathExpr(indexOne)
        val expr3 = XPathReference.getPathExpr(indexTwo)
        if (!exprWild.matches(expr2)) {
            fail("Bad Matching: $wildcardIndex should match $indexOne")
        }
        if (!expr2.matches(exprWild)) {
            fail("Bad Matching: $indexOne should match $wildcardIndex")
        }
        if (expr2.matches(expr3)) {
            fail("Bad Matching: $indexOne should  not match $indexTwo")
        }

        try {
            testEval("null-proto()", null, ec, XPathUnhandledException())
            fail("Did not get expected null pointer")
        } catch (npe: NullPointerException) {
            //expected
        }

        ec.addFunctionHandler(read)
        ec.addFunctionHandler(write)

        read.`val` = "testing-read"
        testEval("read()", null, ec, "testing-read")

        testEval("write('testing-write')", null, ec, true)
        if ("testing-write" != write.`val`) {
            fail("Custom function handler did not successfully send data to external source")
        }

        addDataRef(instance, "/data/string", StringData("string"))
        addDataRef(instance, "/data/int", IntegerData(17))
        addDataRef(instance, "/data/int_two", IntegerData(5))
        addDataRef(instance, "/data/string_two", StringData("2"))
        addDataRef(instance, "/data/predtest[1]/@val", StringData("2.0"))
        addDataRef(instance, "/data/predtest[2]/@val", StringData("2"))
        addDataRef(instance, "/data/predtest[1]/@num", StringData("2.0"))
        addDataRef(instance, "/data/predtest[2]/@num", StringData("2"))
        addDataRef(instance, "/data/predtest[3]/@val", StringData("string"))

        addDataRef(instance, "/data/strtest[1]/@val", StringData("a"))
        addDataRef(instance, "/data/strtest[2]/@val", StringData("b"))
        addDataRef(instance, "/data/strtest[3]/@val", StringData("string"))

        addDataRef(instance, "/data/rangetest[1]/@num", StringData("-2"))
        addDataRef(instance, "/data/rangetest[2]/@num", StringData("3"))

        testEval("/data/string", instance, null, "string")
        testEval("/data/int", instance, null, 17.0)

        testEval("min(/data/int, /data/int_two)", instance, null, 5.0)

        testEval("count(/data/predtest[@val = /data/string_two])", instance, null, 2.0)
        testEval("count(/data/predtest[@val = 2])", instance, null, 2.0)
        testEval("count(/data/predtest[2 = @val])", instance, null, 2.0)

        testEval("count(/data/strtest[@val = 'a'])", instance, null, 1.0)
        testEval("count(/data/strtest[@val = 2])", instance, null, 0.0)
        testEval("count(/data/strtest[@val = /data/string])", instance, null, 1.0)

        testEval("sum(/data/predtest/@num)", instance, null, 4.0)
        testEval("concat(/data/predtest/@num)", instance, null, "2.02")
        testEval("sum(1)", instance, null, XPathTypeMismatchException())

        testEval("checklist(-1, 2, /data/predtest[1]/@val = 2, /data/predtest[2]/@val = 2, /data/predtest[3]/@val = 2)", instance, null, true)
        testEval("checklist(1, 2, /data/predtest[1]/@val = 2, /data/predtest[2]/@val = 2, /data/predtest[3]/@val = 2)", instance, null, true)
        testEval("checklist(-1, 1, /data/predtest[1]/@val = 2, /data/predtest[2]/@val = 2, /data/predtest[3]/@val = 2)", instance, null, false)
        testEval("checklist(3, 4, /data/predtest[1]/@val = 2, /data/predtest[2]/@val = 2, /data/predtest[3]/@val = 2)", instance, null, false)

        testEval("weighted-checklist(-1, 2, /data/predtest[1]/@val = 2, 1, /data/predtest[2]/@val = 2, 1, /data/predtest[3]/@val = 2, 1)", instance, null, true)
        testEval("weighted-checklist(1, 2, /data/predtest[1]/@val = 2, 1, /data/predtest[2]/@val = 2, 1, /data/predtest[3]/@val = 2, 1)", instance, null, true)
        testEval("weighted-checklist(-1, 1, /data/predtest[1]/@val = 2, 1, /data/predtest[2]/@val = 2, 1, /data/predtest[3]/@val = 2, 1)", instance, null, false)
        testEval("weighted-checklist(3, 4, /data/predtest[1]/@val = 2, 1, /data/predtest[2]/@val = 2, 1, /data/predtest[3]/@val = 2, 1)", instance, null, false)

        testEval("max(/data/rangetest[0])", instance, null, Double.NaN)
        testEval("min(/data/rangetest[0])", instance, null, Double.NaN)
        testEval("max(/data/rangetest/@num)", instance, null, 3.0)
        testEval("min(/data/rangetest/@num)", instance, null, -2.0)
    }

    private fun configureLocaleForCalendar() {
        // Reset global state that may have been polluted by earlier test suites
        org.javarosa.core.services.locale.LocalizerManager.init(true)
        Localization.getGlobalLocalizerAdvanced().addAvailableLocale("default")
        Localization.setLocale("default")
        val localeData = TableLocaleSource()
        localeData.setLocaleMapping("ethiopian_months",
                "Mäskäräm,T'ïk'ïmt,Hïdar,Tahsas,T'ïr,Yäkatit,Mägabit,Miyaziya,Gïnbot,Säne,Hämle,Nähäse,P'agume")
        localeData.setLocaleMapping("nepali_months",
                "Baishakh,Jestha,Ashadh,Shrawan,Bhadra,Ashwin,Kartik,Mangsir,Poush,Magh,Falgun,Chaitra")
        Localization.getGlobalLocalizerAdvanced().registerLocaleResource("default", localeData)
    }

    /**
     * Test the ability of the engine to rebuild expressions which are common sources
     * of confusion or user errors
     */
    @Test
    @Throws(XPathSyntaxException::class)
    fun testStringOutputs() {
        testStringOutput("/data//something")
        testStringOutput("invalidfunction('something')/somestep")
        testStringOutput("/data/../relative")
        testStringOutput("/data/@attr/@secondattr")
    }

    @Throws(XPathSyntaxException::class)
    fun testStringOutput(xPathInput: String) {
        val expr = XPathParseTool.parseXPath(xPathInput)!!
        Assert.assertEquals(xPathInput, expr.toPrettyString())
    }

    @Test
    fun testDoNotInferScientificNotationAsDouble() {
        val dbl = FunctionUtils.InferType("100E5")
        Assert.assertTrue("We should not evaluate strings with scientific notation as doubles",
                XPathEqExpr.testEquality(dbl, "100E5"))
    }

    @Test
    fun testOverrideNow() {
        val ec = EvaluationContext(null as FormInstance?)

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "now"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(arrayOf<Class<*>>())
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = "pass"
        })

        testEval("now()", null, ec, "pass")
    }

    // Utility methods for string encryption.
    @Throws(Exception::class)
    private fun generateSecretKey(keyLength: Int): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(keyLength, SecureRandom())
        return keyGen.generateKey()
    }

    @Throws(UnsupportedEncodingException::class)
    fun encryptAndCompare(ec: EvaluationContext, algorithm: String,
                          keyLength: Int, message: String,
                          expectedException: Exception?) {
        val secretKey: SecretKey
        try {
            secretKey = generateSecretKey(keyLength)
        } catch (ex: Exception) {
            fail("Unexpected exception generating secret key")
            return
        }

        // The encrypted output contains a random initialization vector, so
        // we can't know in advance what it will be. Instead decrypt the output
        // and check for the input message.
        val keyString = String(Base64.getEncoder().encode(secretKey.encoded), Charsets.UTF_8)
        try {
            val result = evalExpr("encrypt-string('$message','$keyString','$algorithm')",
                    null, ec)
            val resultString = FunctionUtils.toString(result)

            val decryptedObject = evalExpr("decrypt-string('$resultString','$keyString','$algorithm')",
                    null, ec)
            val decryptedMessage = FunctionUtils.toString(decryptedObject)
            if (message != decryptedMessage) {
                fail("Expected decrypted message $message, got $decryptedMessage")
            }
        } catch (ex: Exception) {
            assertExceptionExpected(expectedException != null, expectedException, ex)
            return
        }
    }

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun testEncryptString() {
        // Ensure JVM-specific XPath functions (encrypt/decrypt) are registered
        org.javarosa.xpath.expr.JvmXPathFunctions.ensureRegistered()
        val keyLengthBit = 256
        val ec = getFunctionHandlers()
        // Valid inputs that should decrypt to themselves.
        encryptAndCompare(ec, "AES", keyLengthBit, "49812057128", null)
        encryptAndCompare(ec, "AES", keyLengthBit,
                "A short message to be encrypted", null)
        encryptAndCompare(ec, "AES", keyLengthBit,
                "A longer message to be encrypted by the AES GCM " +
                        "method, which will test that somewhat longer " +
                        "messages can be correctly encrypted", null)

        // Invalid inputs that should raise exceptions.
        encryptAndCompare(ec, "DES", keyLengthBit,
                "A short message to be encrypted",
                XPathException())
        encryptAndCompare(ec, "AES", keyLengthBit / 2,
                "A short message to be encrypted",
                XPathException())
    }

    protected fun addDataRef(dm: FormInstance, ref: String, data: IAnswerData?) {
        var treeRef = XPathReference.getPathExpr(ref).getReference()
        treeRef = inlinePositionArgs(treeRef)

        addNodeRef(dm, treeRef)

        if (data != null) {
            dm.resolveReference(treeRef)!!.setValue(data)
        }
    }

    private fun inlinePositionArgs(treeRef: TreeReference): TreeReference {
        //find/replace position predicates
        for (i in 0 until treeRef.size()) {
            val predicates = treeRef.getPredicate(i)
            if (predicates == null || predicates.size == 0) {
                continue
            }
            if (predicates.size > 1) {
                throw IllegalArgumentException("only position [] predicates allowed")
            }
            if (predicates[0] !is XPathNumericLiteral) {
                throw IllegalArgumentException("only position [] predicates allowed")
            }
            val d = (predicates[0] as XPathNumericLiteral).d
            if (d != d.toInt().toDouble()) {
                throw IllegalArgumentException("invalid position: $d")
            }

            val multiplicity = d.toInt() - 1
            if (treeRef.getMultiplicity(i) != TreeReference.INDEX_UNBOUND) {
                throw IllegalArgumentException("Cannot inline already qualified steps")
            }
            treeRef.setMultiplicity(i, multiplicity)
        }

        return treeRef.removePredicates()
    }

    private fun addNodeRef(dm: FormInstance, treeRef: TreeReference) {
        var lastValidStep = dm.getRoot()
        for (i in 1 until treeRef.size()) {
            var step = dm.resolveReference(treeRef.getSubReference(i))
            if (step == null) {
                if (treeRef.getMultiplicity(i) == TreeReference.INDEX_ATTRIBUTE) {
                    //must be the last step
                    lastValidStep.setAttribute(null, treeRef.getName(i)!!, "")
                    return
                }
                val currentName = treeRef.getName(i)!!
                step = TreeElement(currentName,
                        if (treeRef.getMultiplicity(i) == TreeReference.INDEX_UNBOUND)
                            TreeReference.DEFAULT_MUTLIPLICITY
                        else
                            treeRef.getMultiplicity(i))
                lastValidStep.addChild(step)
            }
            lastValidStep = step
        }
    }

    fun createTestInstance(): FormInstance {
        val data = TreeElement("data")
        data.addChild(TreeElement("path"))
        return FormInstance(data)
    }

    protected fun getFunctionHandlers(): EvaluationContext {
        val ec = EvaluationContext(null as FormInstance?)
        val allPrototypes = arrayOf(
                arrayOf<Class<*>>(Double::class.java, Double::class.java),
                arrayOf<Class<*>>(Double::class.java),
                arrayOf<Class<*>>(String::class.java, String::class.java),
                arrayOf<Class<*>>(Double::class.java, String::class.java, Boolean::class.java),
                arrayOf<Class<*>>(Boolean::class.java),
                arrayOf<Class<*>>(Boolean::class.java, Double::class.java, String::class.java, Date::class.java, CustomType::class.java)
        )

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "testfunc"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(arrayOf<Class<*>>())
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = java.lang.Boolean.TRUE
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "add"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(allPrototypes[0])
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? =
                    java.lang.Double.valueOf((args!![0] as Double) + (args[1] as Double))
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "proto"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(allPrototypes[0])
                p.add(allPrototypes[1])
                p.add(allPrototypes[2])
                p.add(allPrototypes[3])
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = printArgs(args!!)
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "raw"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(allPrototypes[3])
                return p
            }

            override fun rawArgs(): Boolean = true

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = printArgs(args!!)
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "null-proto"

            @Suppress("UNCHECKED_CAST")
            override fun getPrototypes(): ArrayList<*> = null as ArrayList<*>

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = java.lang.Boolean.FALSE
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "convertible"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(arrayOf<Class<*>>())
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? {
                return object : IExprDataType {
                    override fun toBoolean(): Boolean = true

                    override fun toNumeric(): Double = 5.0

                    override fun toString(): String = "hi"
                }
            }
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "inconvertible"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(arrayOf<Class<*>>())
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = Any()
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "get-custom"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(allPrototypes[4])
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? =
                    if (args!![0] as Boolean) CustomSubType() else CustomType()
        })

        ec.addFunctionHandler(object : IFunctionHandler {
            override fun getName(): String = "check-types"

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Any>()
                p.add(allPrototypes[5])
                return p
            }

            override fun rawArgs(): Boolean = false

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? {
                if (args!!.size != 5 || args[0] !is Boolean || args[1] !is Double ||
                        args[2] !is String || args[3] !is Date || args[4] !is CustomType)
                    fail("Types in custom function handler not converted properly/prototype not matched properly")

                return java.lang.Boolean.TRUE
            }
        })
        return ec
    }

    private fun getVariableContext(): EvaluationContext {
        val ec = EvaluationContext(null as FormInstance?)

        ec.setVariable("var_float_five", java.lang.Float.valueOf(5.0f))
        ec.setVariable("var_string_five", "five")
        ec.setVariable("var_int_five", Integer.valueOf(5))
        ec.setVariable("var_double_five", java.lang.Double.valueOf(5.0))

        return ec
    }

    private fun printArgs(oa: Array<Any?>): String {
        val sb = StringBuffer()
        sb.append("[")
        for (i in oa.indices) {
            val item = oa[i]!!
            val fullName = item.javaClass.name
            val lastIndex = Math.max(fullName.lastIndexOf('.'), fullName.lastIndexOf('$'))
            sb.append(fullName.substring(lastIndex + 1, fullName.length))
            sb.append(":")
            sb.append(if (item is Date) DateUtils.formatDate(item, DateUtils.FORMAT_ISO8601) else item.toString())
            if (i < oa.size - 1)
                sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    private open inner class CustomType {
        override fun toString(): String = ""

        override fun equals(other: Any?): Boolean = other is CustomType
    }

    private inner class CustomSubType : CustomType()

    /* unused
    private inner class CustomAnswerData : IAnswerData {
        override fun getDisplayText(): String = "custom"
        override fun getValue(): Any = CustomType()
        override fun setValue(o: Any) { }
        override fun readExternal(in: DataInputStream, pf: PrototypeFactory) { }
        override fun writeExternal(out: DataOutputStream) { }
        override fun clone(): IAnswerData = CustomAnswerData()
    }
    */

    internal abstract inner class StatefulFunc : IFunctionHandler {
        var `val`: String? = null

        override fun rawArgs(): Boolean = false
    }

    internal val read: StatefulFunc = object : StatefulFunc() {
        override fun getName(): String = "read"

        override fun getPrototypes(): ArrayList<*> {
            val p = ArrayList<Any>()
            p.add(arrayOf<Class<*>>())
            return p
        }

        override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? = `val`!!
    }

    internal val write: StatefulFunc = object : StatefulFunc() {
        override fun getName(): String = "write"

        override fun getPrototypes(): ArrayList<*> {
            val p = ArrayList<Any>()
            val proto = arrayOf<Class<*>>(String::class.java)
            p.add(proto)
            return p
        }

        override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? {
            `val` = args!![0] as String
            return java.lang.Boolean.TRUE
        }
    }
}
