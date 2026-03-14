package org.javarosa.test_utils

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.utils.InstanceUtils
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Assert.fail
import kotlin.math.abs

/**
 * Commonly used utilities for evaluating xpath expressions.
 *
 * @author Phillip Mates
 */
object ExprEvalUtils {
    private const val DOUBLE_TOLERANCE = 1.0e-12

    /**
     * Parse and evaluate an xpath expression and check that the result matches
     * the designated expected result.
     *
     * @param rawExpr   Unparsed xpath expression
     * @param model     Representation of form DOM used to evaluate expression against.
     * @param evalCtx   Particular context that the expression should be evaluated.
     *                  If null, build from the model argument.
     * @param expected  The expected result of evaluating the xpath expression.
     *                  Can be an exception object.
     * @param tolerance Acceptable numerical difference in expected and resulting values.
     *                  If null, use the default tolerance.
     * @return Empty string if the result of evaluating the expression is
     *         equivalent to the expected value. Otherwise, an error message detailing what went wrong.
     */
    @JvmStatic
    fun expectedEval(
        rawExpr: String,
        model: FormInstance?,
        evalCtx: EvaluationContext?,
        expected: Any,
        tolerance: Double?
    ): String {
        val actualTolerance = tolerance ?: DOUBLE_TOLERANCE
        val exceptionExpected = expected is XPathException
        val ctx = evalCtx ?: EvaluationContext(model)

        val expr: XPathExpression?
        try {
            expr = XPathParseTool.parseXPath(rawExpr)
        } catch (xpse: XPathSyntaxException) {
            return "Parsing syntax error for $rawExpr"
        }

        if (expr == null) {
            return "Parsing $rawExpr resulted in a null expression."
        }

        try {
            val result = FunctionUtils.unpack(expr.eval(model, ctx))

            return when {
                exceptionExpected -> "Expected exception, expression : $rawExpr"
                result is Double && expected is Double -> {
                    if (abs(result - expected) > actualTolerance) {
                        "Doubles outside of tolerance: got $result, expected $expected"
                    } else ""
                }
                expected != result -> "Expected $expected, got $result"
                else -> ""
            }
        } catch (xpathEx: XPathException) {
            return if (!exceptionExpected) {
                "Did not expect ${xpathEx::class} exception"
            } else if (xpathEx::class != expected::class) {
                "Did not get expected exception type"
            } else ""
        }
    }

    @JvmStatic
    @Throws(XPathSyntaxException::class)
    fun assertEqualsXpathEval(
        failureMessage: String,
        expectedOutput: Any,
        input: String,
        evalContext: EvaluationContext
    ) {
        val evalResult = xpathEval(evalContext, input)
        Assert.assertEquals(failureMessage, expectedOutput, evalResult)
    }

    @JvmStatic
    @Throws(XPathSyntaxException::class)
    fun assertAlmostEqualsXpathEval(
        expectedOutput: Double,
        tolerance: Double,
        input: String,
        evalContext: EvaluationContext
    ) {
        val evalResult = xpathEval(evalContext, input) as Double
        val difference = abs(evalResult - expectedOutput)
        Assert.assertTrue("Evaluated result and expected output differ by $difference",
            difference < tolerance)
    }

    @JvmStatic
    @Throws(XPathSyntaxException::class)
    fun xpathEval(evalContext: EvaluationContext, input: String): Any? {
        val expr = XPathParseTool.parseXPath(input)!!
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    @JvmStatic
    fun testEval(expr: String, ec: EvaluationContext, expected: Any) {
        testEval(expr, null, ec, expected, DOUBLE_TOLERANCE)
    }

    @JvmStatic
    fun testEval(expr: String, model: FormInstance?, ec: EvaluationContext?, expected: Any) {
        testEval(expr, model, ec, expected, DOUBLE_TOLERANCE)
    }

    @JvmStatic
    fun testEval(
        expr: String,
        model: FormInstance?,
        ec: EvaluationContext?,
        expected: Any,
        tolerance: Double
    ) {
        val exceptionExpected = expected is XPathException
        val evalCtx = ec ?: EvaluationContext(model)

        val xpe: XPathExpression
        try {
            xpe = XPathParseTool.parseXPath(expr) ?: run {
                fail("Null expression $expr")
                return
            }
        } catch (xpse: XPathSyntaxException) {
            fail("Null expression or syntax error $expr")
            return
        }

        try {
            val result = FunctionUtils.unpack(xpe.eval(model, evalCtx))
            if (tolerance != DOUBLE_TOLERANCE) {
                println("$expr = $result")
            }

            when {
                exceptionExpected -> fail("Expected exception, expression : $expr")
                result is Double && expected is Double -> {
                    if (abs(result - expected) > tolerance) {
                        fail("Doubles outside of tolerance: got $result, expected $expected")
                    }
                }
                expected != result -> fail("Expected $expected, got $result")
            }
        } catch (xpex: XPathException) {
            if (!exceptionExpected) {
                xpex.printStackTrace()
                fail("Did not expect ${xpex::class} exception")
            } else if (xpex::class != expected::class) {
                fail("Did not get expected exception type")
            }
        }
    }

    /**
     * Load a form instance from a path.
     * Doesn't create a model or main instance.
     *
     * @param formPath path of the form to load, relative to project build
     * @return FormInstance created from the path pointed to, or null if any error occurs.
     */
    @JvmStatic
    fun loadInstance(formPath: String): FormInstance? {
        return try {
            InstanceUtils.loadFormInstance(formPath)
        } catch (e: Exception) {
            fail("Unable to load form at $formPath: ${e.message}")
            null
        }
    }
}
