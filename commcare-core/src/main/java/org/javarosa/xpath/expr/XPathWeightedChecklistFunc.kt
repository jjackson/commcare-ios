package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathWeightedChecklistFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (!(args.size >= 2 && args.size % 2 == 0)) {
            throw XPathArityException(name, "an even number of arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        if (args.size == 4 && evaluatedArgs[2] is XPathNodeset && evaluatedArgs[3] is XPathNodeset) {
            val factors = (evaluatedArgs[2] as XPathNodeset).toArgList()
            val weights = (evaluatedArgs[3] as XPathNodeset).toArgList()
            if (factors.size != weights.size) {
                throw XPathTypeMismatchException("weighted-checklist: nodesets not same length")
            }
            return checklistWeighted(evaluatedArgs[0], evaluatedArgs[1], factors, weights)
        } else {
            return checklistWeighted(
                evaluatedArgs[0], evaluatedArgs[1],
                FunctionUtils.subsetArgList(evaluatedArgs, 2, 2),
                FunctionUtils.subsetArgList(evaluatedArgs, 3, 2)
            )
        }
    }

    companion object {
        const val NAME: String = "weighted-checklist"
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * very similar to checklist, only each factor is assigned a real-number 'weight'.
         *
         * the first and second args are again the minimum and maximum, but -1 no longer means
         * 'not applicable'.
         *
         * subsequent arguments come in pairs: first the boolean value, then the floating-point
         * weight for that value
         *
         * the weights of all the 'true' factors are summed, and the function returns whether
         * this sum is between the min and max
         */
        private fun checklistWeighted(oMin: Any?, oMax: Any?, flags: Array<Any?>, weights: Array<Any?>): Boolean {
            val min = FunctionUtils.toNumeric(oMin)
            val max = FunctionUtils.toNumeric(oMax)

            var sum = 0.0
            for (i in flags.indices) {
                val flag = FunctionUtils.toBoolean(flags[i])
                val weight = FunctionUtils.toNumeric(weights[i])

                if (flag)
                    sum += weight
            }

            return sum >= min && sum <= max
        }
    }
}
