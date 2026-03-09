package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathPowFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return power(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "pow"
        private const val EXPECTED_ARG_COUNT: Int = 2

        /**
         * Best faith effort at getting a result for math.pow
         *
         * @param o1 The base number
         * @param o2 The exponent of the number that it is to be raised to
         * @return An approximation of o1 ^ o2. If there is a native power
         * function, it is utilized. It there is not, a recursive exponent is
         * run if (b) is an integer value, and a taylor series approximation is
         * used otherwise.
         */
        private fun power(o1: Any?, o2: Any?): Double {
            val a = FunctionUtils.toDouble(o1)
            val b = FunctionUtils.toDouble(o2)

            return Math.pow(a, b)
        }
    }
}
