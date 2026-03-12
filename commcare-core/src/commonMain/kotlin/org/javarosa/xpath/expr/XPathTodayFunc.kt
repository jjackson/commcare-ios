package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.model.utils.PlatformDate

open class XPathTodayFunc : XPathFuncExpr, VolatileXPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, false)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return DateUtils.roundDate(PlatformDate())
    }

    override fun rootExpressionTypeIsCacheable(): Boolean {
        return false
    }

    companion object {
        const val NAME: String = "today"
        private const val EXPECTED_ARG_COUNT: Int = 0
    }
}
