package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathSubstringAfterFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return substringAfter(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "substring-after"
        private const val EXPECTED_ARG_COUNT: Int = 2

        private fun substringAfter(fullStringAsRaw: Any?, substringAsRaw: Any?): String {
            val fullString = FunctionUtils.toString(fullStringAsRaw)
            val subString = FunctionUtils.toString(substringAsRaw)

            if (fullString.length == 0) {
                return ""
            }

            val substringIndex = fullString.indexOf(subString)
            return if (substringIndex == -1) {
                fullString
            } else {
                fullString.substring(substringIndex + subString.length, fullString.length)
            }
        }
    }
}
