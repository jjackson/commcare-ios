package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.DataUtil
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException

// non-standard

/**
 * return the number of choices in a multi-select answer
 * (i.e, space-delimited choice values)
 */
open class XPathCountSelectedFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        val evalResult = FunctionUtils.unpack(evaluatedArgs[0])
        if (evalResult !is String) {
            throw XPathTypeMismatchException("count-selected argument was not a select list")
        }

        return DataUtil.splitOnSpaces(evalResult).size.toDouble()
    }

    companion object {
        const val NAME: String = "count-selected"
        private const val EXPECTED_ARG_COUNT: Int = 1
    }
}
