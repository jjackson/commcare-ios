package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathRegexFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return regex(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "regex"
        private const val EXPECTED_ARG_COUNT: Int = 2

        /**
         * determine if a string matches a regular expression.
         *
         * @param o1 string being matched
         * @param o2 regular expression
         */
        private fun regex(o1: Any?, o2: Any?): Boolean {
            val str = FunctionUtils.toString(o1)
            val re = FunctionUtils.toString(o2)

            try {
                return Regex(re).containsMatchIn(str)
            } catch (e: IllegalArgumentException) {
                throw XPathException("The regular expression '$re' is invalid.")
            }
        }
    }
}
