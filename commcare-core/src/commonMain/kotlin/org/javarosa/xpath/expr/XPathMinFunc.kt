package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.parser.XPathSyntaxException
import kotlin.math.min

open class XPathMinFunc : XPathFuncExpr {
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
            min((evaluatedArgs[0] as XPathNodeset).toArgList())
        } else {
            min(evaluatedArgs)
        }
    }

    companion object {
        const val NAME: String = "min"
        // one or more arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        private fun min(argVals: Array<Any?>): Any {
            if (argVals.isEmpty()) {
                return Double.NaN
            }

            var min = Double.MAX_VALUE
            for (argVal in argVals) {
                min = min(min, FunctionUtils.toNumeric(argVal))
            }
            return min
        }
    }
}
