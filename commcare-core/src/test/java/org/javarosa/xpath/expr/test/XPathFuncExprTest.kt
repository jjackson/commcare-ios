package org.javarosa.xpath.expr.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.javarosa.test_utils.ClassLoadUtils
import org.javarosa.test_utils.ExprEvalUtils
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathCustomRuntimeFunc
import org.javarosa.xpath.expr.XPathFuncExpr
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.URISyntaxException

/**
 * Tests for xpath functions
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class XPathFuncExprTest {

    @Test
    fun testOutOfBoundsSelect() {
        val instance = ExprEvalUtils.loadInstance("/test_xpathpathexpr.xml")

        ExprEvalUtils.testEval("selected-at('hello there', 0)", instance, null, "hello")
        ExprEvalUtils.testEval("selected-at('hello there', 1)", instance, null, "there")
        ExprEvalUtils.testEval("selected-at('hello there', 2)", instance, null, XPathException())
    }

    @Test
    fun testNodesetJoins() {
        val instance = ExprEvalUtils.loadInstance("/test_xpathpathexpr.xml")
        ExprEvalUtils.testEval("join(' ', /data/places/country/state)", instance, null, "Utah Montana")
        ExprEvalUtils.testEval("join-chunked('-', 5, /data/places/country/state)", instance, null, "UtahM-ontan-a")
    }

    @Test
    fun testSubstringVariants() {
        val instance = ExprEvalUtils.loadInstance("/test_xpathpathexpr.xml")

        ExprEvalUtils.testEval("substring-before('Utah Montana', /data/places/country/state[2])", instance, null, "Utah ")
        ExprEvalUtils.testEval("substring-before(join(' ', /data/places/country/state), /data/places/country/state[2])", instance, null, "Utah ")
        ExprEvalUtils.testEval("substring-before('hello there', ' there')", instance, null, "hello")
        ExprEvalUtils.testEval("substring-before('hello there there', ' there')", instance, null, "hello")
        ExprEvalUtils.testEval("substring-before('hello there', '')", instance, null, "")
        ExprEvalUtils.testEval("substring-before('hello there', 'foo')", instance, null, "")
        ExprEvalUtils.testEval("substring-before('', 'foo')", instance, null, "")
        ExprEvalUtils.testEval("substring-before('123', 2)", instance, null, "1")

        ExprEvalUtils.testEval("substring-after('Utah Montana', /data/places/country/state[1])", instance, null, " Montana")
        ExprEvalUtils.testEval("substring-after(join(' ', /data/places/country/state), /data/places/country/state[1])", instance, null, " Montana")
        ExprEvalUtils.testEval("substring-after('hello there', 'hello ')", instance, null, "there")
        ExprEvalUtils.testEval("substring-after('hello hello there', 'hello ')", instance, null, "hello there")
        ExprEvalUtils.testEval("substring-after('hello there', '')", instance, null, "hello there")
        ExprEvalUtils.testEval("substring-after('hello there', 'foo')", instance, null, "hello there")
        ExprEvalUtils.testEval("substring-after('', 'foo')", instance, null, "")
        ExprEvalUtils.testEval("substring-after('123', 2)", instance, null, "3")
    }

    @Test
    fun testDistinct() {
        val instance = ExprEvalUtils.loadInstance("/xpath/test_distinct.xml")

        ExprEvalUtils.testEval("join(' ', distinct-values(/data/places/country/@id))", instance, null, "us ca mx")
        ExprEvalUtils.testEval("join(' ', distinct-values(/data/places/country/@continent))", instance, null, "na")
        ExprEvalUtils.testEval("join(' ', distinct-values(/data/places/country/language))", instance, null, "English Spanish")
        ExprEvalUtils.testEval("join(' ', distinct-values(/data/places/country[language = 'French']/@id))", instance, null, "")
        ExprEvalUtils.testEval("join(' ', distinct-values('a b c a b d'))", instance, null, "a b c d")
    }

    @Test
    fun testIndexOf() {
        val instance = ExprEvalUtils.loadInstance("/xpath/test_distinct.xml")

        ExprEvalUtils.testEval("index-of(/data/places/country/@id, 'ca')", instance, null, 1.0)
        ExprEvalUtils.testEval("index-of('ma ks ca', 'ca')", instance, null, 2.0)
        ExprEvalUtils.testEval("index-of(distinct-values(/data/places/country/@continent), 'na')", instance, null, 0.0)
        ExprEvalUtils.testEval("index-of(/data/places/country/@id, 'NOTHING')", instance, null, "")
    }

    @Test
    fun testSleep() {
        ExprEvalUtils.testEval("sleep(50, 'test')", null, null, "test")

        val time = System.currentTimeMillis()
        val r = Runnable { ExprEvalUtils.testEval("sleep(50000, 'test')", null, null, "test") }
        val t = Thread(r)
        t.start()
        t.interrupt()
        try {
            t.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val duration = System.currentTimeMillis() - time
        if (duration > 1000) {
            Assert.fail("sleep function did not properly respond to interrupt signal")
        }
    }

    /**
     * Test that `position(some_ref)` throws a XPathTypeMismatchException when
     * some_ref points to an empty nodeset
     */
    @Test
    @Throws(XPathSyntaxException::class)
    fun testOutOfBoundsPosition() {
        val fpi = FormParseInit("/xform_tests/test_position_with_ref.xml")
        val fec = fpi.getFormEntryController()
        fpi.getFormDef()!!.initialize(true, null)
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        try {
            do {
            } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
        } catch (e: XPathTypeMismatchException) {
            return
        }
        fail("form entry should fail on bad `position` usage before getting here")
    }

    @Test
    fun testCoalesce() {
        val instance = ExprEvalUtils.loadInstance("/test_xpathpathexpr.xml")

        ExprEvalUtils.testEval("/data/places/country[@id = 'one']/name", instance, null, "Singapore")
        ExprEvalUtils.testEval("/data/places/country[@id = 'three']/name", instance, null, "")
        ExprEvalUtils.testEval("coalesce(/data/places/country[@id = 'three']/name, /data/places/country[@id = 'one']/name)", instance, null, "Singapore")

        ExprEvalUtils.testEval("coalesce('', '', /data/places/country[@id = 'one']/name)", instance, null, "Singapore")
        ExprEvalUtils.testEval("coalesce('', /data/places/country[@id = 'three']/name, /data/places/country[@id = 'one']/name)", instance, null, "Singapore")
        ExprEvalUtils.testEval("coalesce('', /data/places/country[@id = 'one']/name, /data/places/country[@id = 'two']/name)", instance, null, "Singapore")
        ExprEvalUtils.testEval("coalesce('', '', '', '', '')", instance, null, "")
    }

    @Test
    fun testCond() {
        val instance = ExprEvalUtils.loadInstance("/test_xpathpathexpr.xml")
        ExprEvalUtils.testEval("cond(true(), 0, 1=1, 1, -1)", instance, null, 0.0)
        ExprEvalUtils.testEval("cond(false(), 0, 1)", instance, null, 1.0)
        ExprEvalUtils.testEval("cond('a', 0, 1)", instance, null, 0.0)
        ExprEvalUtils.testEval("cond(1 = 0, 0, cond(1 = 1, true(), false()), 1, 0)", instance, null, 1.0)

        assertParseFailure("cond('a' = 'a')")
        assertParseFailure("cond('a' = 'a', 0)")
        assertParseFailure("cond('a' = 'a', 0, false(), 0)")
        assertParseFailure("cond('a', 0, *1)")
    }

    companion object {
        private fun assertParseFailure(exprString: String) {
            var didParseFail = false
            try {
                XPathParseTool.parseXPath(exprString)
            } catch (xpse: XPathSyntaxException) {
                didParseFail = true
            }
            assertTrue(didParseFail)
        }
    }

    /**
     * Ensure that static list of xpath functions is kept up-to-date
     */
    @Test
    @Throws(ClassNotFoundException::class, IOException::class, URISyntaxException::class)
    fun funcListTest() {
        val cls = ClassLoadUtils.getClasses(XPathFuncExpr::class.java.`package`.name)
        val funcClasses = ClassLoadUtils.classesThatExtend(cls, XPathFuncExpr::class.java).toMutableList()

        funcClasses.remove(XPathCustomRuntimeFunc::class.java)

        val funcList = FunctionUtils.getXPathFuncListMap()

        val registeredClasses = HashSet<Class<*>>()
        for (factory in funcList.values) {
            registeredClasses.add(factory.invoke().javaClass)
        }

        for (c in funcClasses) {
            assertTrue("$c is not in list of functions, please update it",
                    registeredClasses.contains(c))
        }
        for (c in registeredClasses) {
            assertTrue("$c is in the list of functions but no longer exists, please remove it.",
                    funcClasses.contains(c))
        }
    }
}
