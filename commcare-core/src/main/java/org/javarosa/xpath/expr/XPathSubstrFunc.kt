package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathSubstrFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (!(args.size == 2 || args.size == 3)) {
            throw XPathArityException(name, "two or three arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return substring(evaluatedArgs[0], evaluatedArgs[1], if (args.size == 3) evaluatedArgs[2] else null)
    }

    companion object {
        const val NAME: String = "substr"
        // 2 or 3 arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * Implementation decisions:
         * -Returns the empty string if o1.equals("")
         * -Returns the empty string for any inputs that would
         * cause an IndexOutOfBoundsException on call to Java's substring method,
         * after start and end have been adjusted
         */
        private fun substring(o1: Any?, o2: Any?, o3: Any?): String {
            val s = FunctionUtils.toString(o1)

            if (s.length == 0) {
                return ""
            }

            var start = FunctionUtils.toInt(o2).toInt()

            val len = s.length

            var end = if (o3 != null) FunctionUtils.toInt(o3).toInt() else len
            if (start < 0) {
                start = len + start
            }
            if (end < 0) {
                end = len + end
            }
            start = minOf(maxOf(0, start), end)
            end = minOf(maxOf(0, end), end)

            return if (start <= end && end <= len) s.substring(start, end) else ""
        }
    }
}
