package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathJoinChunkFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 3) {
            throw XPathArityException(name, "at least three arguments", args.size)
        }
    }

    override fun evalBody(
        model: DataInstance<*>?, evalContext: EvaluationContext,
        evaluatedArgs: Array<Any?>
    ): Any {
        return if (args.size == 3 && evaluatedArgs[2] is XPathNodeset) {
            join(
                evaluatedArgs[0], evaluatedArgs[1],
                (evaluatedArgs[2] as XPathNodeset).toArgList()
            )
        } else {
            join(
                evaluatedArgs[0], evaluatedArgs[1],
                FunctionUtils.subsetArgList(evaluatedArgs, 2)
            )
        }
    }

    companion object {
        const val NAME: String = "join-chunked"
        // one or more args. Function overrides validate arg count
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * concatenate an abritrary-length argument list of string values together
         */
        @JvmStatic
        fun join(oSep: Any?, oChunkSize: Any?, argVals: Array<Any?>): String {
            val sep = FunctionUtils.toString(oSep)
            val chunkSize = FunctionUtils.toInt(oChunkSize).toInt()
            val intermediateBuffer = StringBuffer()
            val outputBuffer = StringBuffer()

            for (i in argVals.indices) {
                intermediateBuffer.append(FunctionUtils.toString(argVals[i]))
            }

            for (i in 0 until intermediateBuffer.length) {
                if (i != 0 && i % chunkSize == 0) {
                    outputBuffer.append(sep)
                }
                outputBuffer.append(intermediateBuffer[i])
            }

            return outputBuffer.toString()
        }
    }
}
