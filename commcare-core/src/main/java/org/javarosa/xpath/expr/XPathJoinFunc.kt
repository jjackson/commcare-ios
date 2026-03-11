package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathJoinFunc : XPathFuncExpr {
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
        val argList: Array<Any?>
        if (args.size == 2 && evaluatedArgs[1] is XPathNodeset) {
            argList = (evaluatedArgs[1] as XPathNodeset).toArgList()
        } else if (args.size == 2 && evaluatedArgs[1] is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            argList = evaluatedArgs[1] as Array<Any?>
        } else {
            argList = FunctionUtils.subsetArgList(evaluatedArgs, 1)
        }

        return join(evaluatedArgs[0], argList)
    }

    companion object {
        const val NAME: String = "join"
        // one or more arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * concatenate an abritrary-length argument list of string values together
         */
        fun join(oSep: Any?, argVals: Array<Any?>): String {
            val sep = FunctionUtils.toString(oSep)
            val sb = StringBuilder()

            for (i in argVals.indices) {
                sb.append(FunctionUtils.toString(argVals[i]))
                if (i < argVals.size - 1)
                    sb.append(sep)
            }

            return sb.toString()
        }
    }
}
