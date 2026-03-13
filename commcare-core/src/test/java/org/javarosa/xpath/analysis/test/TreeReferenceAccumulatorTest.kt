package org.javarosa.xpath.analysis.test

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.TreeReferenceAccumulatingAnalyzer
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for accumulating references from XPath Expressions
 *
 * @author Clayton Sims
 */
class TreeReferenceAccumulatorTest {

    @Test
    @Throws(XPathSyntaxException::class, AnalysisInvalidException::class)
    fun testNonParses() {
        runAndTest("4+4")
        runAndTest("if('steve' = 'bob', 4, \$jim)")
    }

    @Test
    @Throws(XPathSyntaxException::class, AnalysisInvalidException::class)
    fun testBasicParses() {
        runAndTest(
            "instance('casedb')/casedb/case[@case_id = /data/test]/value",
            "instance('casedb')/casedb/case/@case_id",
            "instance('casedb')/casedb/case/value",
            "/data/test"
        )

        runAndTest(
            "if(/data/a + /data/b = '4', /data/a, /data/c)",
            "/data/a",
            "/data/b",
            "/data/c"
        )

        runAndTest(
            "instance('cars')/cars/car[./@make = instance('models')/models/model[@year = '1992']/make]/name",
            "instance('cars')/cars/car/@make",
            "instance('cars')/cars/car/name",
            "instance('models')/models/model/@year",
            "instance('models')/models/model/make"
        )

        runAndTest(
            "instance('cars')/cars/car[../@lead_make = instance('models')/models/model[@year = '1992']/make]/name",
            "instance('cars')/cars/@lead_make",
            "instance('cars')/cars/car/name",
            "instance('models')/models/model/@year",
            "instance('models')/models/model/make"
        )
    }

    @Test
    @Throws(XPathSyntaxException::class, AnalysisInvalidException::class)
    fun testParsesWithContext() {
        val ecBase = EvaluationContext(null)
        val root = EvaluationContext(
            ecBase,
            XPathReference.getPathExpr("instance('baseinstance')/base/element").getReference()
        )
        val nestedRoot = EvaluationContext(
            root,
            XPathReference.getPathExpr("instance('nestedinstance')/nested/element").getReference()
        )

        runAndTest(
            root,
            "instance('expr')/ebase/element[@attr = current()/context_value]/value",
            "instance('expr')/ebase/element/value",
            "instance('expr')/ebase/element/@attr",
            "instance('baseinstance')/base/element/context_value"
        )

        runAndTest(
            nestedRoot,
            "instance('expr')/ebase/element[@attr = current()/context_value]/value",
            "instance('expr')/ebase/element/value",
            "instance('expr')/ebase/element/@attr",
            "instance('nestedinstance')/nested/element/context_value"
        )

        runAndTest(
            root,
            "../other_element",
            "instance('baseinstance')/base/other_element"
        )

        runAndTest(
            root,
            "instance('cars')/cars/car[../@lead_make = instance('models')/models/model[@year = current()/year]/make]/name",
            "instance('cars')/cars/@lead_make",
            "instance('cars')/cars/car/name",
            "instance('models')/models/model/@year",
            "instance('models')/models/model/make",
            "instance('baseinstance')/base/element/year"
        )
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testInvalidReferences() {
        runForError("../relativeRef")
        runForError("current()/relative")
    }

    private fun runForError(text: String) {
        try {
            runAndTest(text)
        } catch (e: AnalysisInvalidException) {
            return
        }
        fail("Should have failed to analyse expression $text")
    }

    private fun runAndTest(text: String, vararg matches: String) {
        runAndTest(null, text, *matches)
    }

    /**
     * Tests that running TreeReferenceAccumulatingAnalyzer on the XPath expression represented
     * by [text] results in accumulating all of the tree refs listed in [matches]
     */
    private fun runAndTest(context: EvaluationContext?, text: String, vararg matches: String) {
        val expression: XPathExpression = try {
            XPathParseTool.parseXPath(text)!!
        } catch (e: XPathSyntaxException) {
            throw RuntimeException(e)
        }

        val analyzer = if (context == null) {
            TreeReferenceAccumulatingAnalyzer()
        } else {
            TreeReferenceAccumulatingAnalyzer(context)
        }

        val results = analyzer.accumulate(expression)
            ?: throw AnalysisInvalidException("Couldn't analyze expression: $text")

        val expressions = matches.mapTo(HashSet<TreeReference>()) {
            XPathReference.getPathExpr(it).getReference()
        }

        assertEquals(text, expressions, results)
    }
}
