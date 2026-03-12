package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathChecklistFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 2) {
            throw XPathArityException(name, "two or more arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return if (args.size == 3 && evaluatedArgs[2] is XPathNodeset) {
            checklist(evaluatedArgs[0], evaluatedArgs[1], (evaluatedArgs[2] as XPathNodeset).toArgList())
        } else {
            checklist(evaluatedArgs[0], evaluatedArgs[1], FunctionUtils.subsetArgList(evaluatedArgs, 2))
        }
    }

    companion object {
        const val NAME: String = "checklist"
        // two or more arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * perform a 'checklist' computation, enabling expressions like 'if there are at least 3 risk
         * factors active'
         *
         * @param oMin    a numeric value expressing the minimum number of factors required.
         *                if -1, no minimum is applicable
         * @param oMax    a numeric value expressing the maximum number of allowed factors.
         *                if -1, no maximum is applicable
         * @param factors individual factors that are coerced to boolean values
         * @return true if the count of 'true' factors is between the applicable minimum and maximum,
         * inclusive
         */
        private fun checklist(oMin: Any?, oMax: Any?, factors: Array<Any?>): Boolean {
            val min = FunctionUtils.toNumeric(oMin).toInt()
            val max = FunctionUtils.toNumeric(oMax).toInt()

            var count = 0
            for (factor in factors) {
                if (FunctionUtils.toBoolean(factor)) {
                    count++
                }
            }

            return (min < 0 || count >= min) && (max < 0 || count <= max)
        }
    }
}
