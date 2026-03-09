package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathBooleanFromStringFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        val s = FunctionUtils.toString(evaluatedArgs[0])
        return if (s.equals("true", ignoreCase = true) || s == "1") {
            java.lang.Boolean.TRUE
        } else {
            java.lang.Boolean.FALSE
        }
    }

    companion object {
        const val NAME: String = "boolean-from-string"
        private const val EXPECTED_ARG_COUNT: Int = 1
    }
}
