package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

/**
 * Identifies the numerical index of the provided argument into the provided sequence, if
 * it is a member of the sequence. If not, an empty result is returned.
 *
 * Created by ctsims on 04/24/2020
 */
open class XPathIndexOfFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        val argList = FunctionUtils.getSequence(evaluatedArgs[0])
        val indexedItem = FunctionUtils.toString(evaluatedArgs[1])

        for (i in argList.indices) {
            if (argList[i] == indexedItem) {
                return java.lang.Double.valueOf(i.toDouble())
            }
        }
        return ""
    }

    companion object {
        const val NAME: String = "index-of"
        // one or more arguments
        private const val EXPECTED_ARG_COUNT: Int = 2
    }
}
