package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathSubstringBeforeFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return substringBefore(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "substring-before"
        private const val EXPECTED_ARG_COUNT: Int = 2

        private fun substringBefore(fullStringAsRaw: Any?, substringAsRaw: Any?): String {
            val fullString = FunctionUtils.toString(fullStringAsRaw)
            val subString = FunctionUtils.toString(substringAsRaw)

            if (fullString.length == 0) {
                return ""
            }

            val substringIndex = fullString.indexOf(subString)
            return if (substringIndex <= 0) {
                ""
            } else {
                fullString.substring(0, substringIndex)
            }
        }
    }
}
