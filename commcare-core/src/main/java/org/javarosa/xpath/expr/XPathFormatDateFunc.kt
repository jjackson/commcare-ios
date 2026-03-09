package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.xpath.parser.XPathSyntaxException
import java.util.Date

open class XPathFormatDateFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return dateStr(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "format-date"
        private const val EXPECTED_ARG_COUNT: Int = 2

        private fun dateStr(od: Any?, of: Any?): String {
            val expandedDate: Date? = FunctionUtils.expandDateSafe(od)
            if (expandedDate == null) {
                return ""
            }
            return DateUtils.format(expandedDate, FunctionUtils.toString(of))
        }
    }
}
