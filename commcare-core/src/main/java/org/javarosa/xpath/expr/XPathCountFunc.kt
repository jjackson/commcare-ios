package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathCountFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        if (evaluatedArgs[0] is XPathNodeset) {
            return java.lang.Double.valueOf((evaluatedArgs[0] as XPathNodeset).size().toDouble())
        } else {
            throw XPathTypeMismatchException("uses an invalid reference inside a count function")
        }
    }

    companion object {
        const val NAME: String = "count"
        private const val EXPECTED_ARG_COUNT: Int = 1
    }
}
