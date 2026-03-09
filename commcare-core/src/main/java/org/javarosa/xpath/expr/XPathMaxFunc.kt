package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathMaxFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 1) {
            throw XPathArityException(name, "at least one argument", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return if (evaluatedArgs.size == 1 && evaluatedArgs[0] is XPathNodeset) {
            max((evaluatedArgs[0] as XPathNodeset).toArgList())
        } else {
            max(evaluatedArgs)
        }
    }

    companion object {
        const val NAME: String = "max"
        // one or more arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * Identify the largest value from the list of provided values.
         */
        private fun max(argVals: Array<Any?>): Any {
            if (argVals.isEmpty()) {
                return Double.NaN
            }

            var max = Double.NEGATIVE_INFINITY
            for (argVal in argVals) {
                max = Math.max(max, FunctionUtils.toNumeric(argVal))
            }
            return max
        }
    }
}
