package org.javarosa.xpath.expr

import org.commcare.cases.util.StringUtils
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathChecksumFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return checksum(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "checksum"
        private const val EXPECTED_ARG_COUNT: Int = 2

        const val ALGORITHM_KEY_VERHOEFF: String = "verhoeff"

        /**
         * @param o1 algorithm type used to calculate checksum. We only support 'verhoeff' for now.
         * @param o2 input we are calculating checksum for
         * @return checksum of [o2] calculated using [o1] type algorithm
         */
        private fun checksum(o1: Any?, o2: Any?): String {
            val algorithmKey = FunctionUtils.toString(o1)
            val input = FunctionUtils.toString(o2)

            return when (algorithmKey) {
                ALGORITHM_KEY_VERHOEFF -> verhoeffChecksum(input)
                else -> throw XPathUnsupportedException("Bad algorithm key $algorithmKey. We only support 'verhoeff' as algorithm key right now.")
            }
        }

        /**
         * Calculates Verhoeff checksum for the given [input] string
         *
         * @param input input string to calculate verhoeff checksum for
         * @return Verhoeff checksum value for [input]
         */
        private fun verhoeffChecksum(input: String): String {
            // The multiplication table
            val op = arrayOf(
                intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
                intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
                intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
                intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
                intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
                intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
                intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
                intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
                intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
            )

            // The permutation table
            val p = arrayOf(
                intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
                intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
                intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
                intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
                intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
                intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
                intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
            )

            // The inverse table
            val inv = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

            val inputList = StringUtils.toList(input)
            inputList.reverse()

            var check = 0
            for (i in inputList.indices) {
                val charAsNum: Int
                try {
                    charAsNum = inputList[i].toString().toInt()
                } catch (e: NumberFormatException) {
                    throw XPathUnsupportedException("Illegal character '${inputList[i]}' in input for Xpath function checksum()")
                }
                check = op[check][p[(i + 1) % 8][charAsNum]]
            }

            return inv[check].toString()
        }
    }
}
