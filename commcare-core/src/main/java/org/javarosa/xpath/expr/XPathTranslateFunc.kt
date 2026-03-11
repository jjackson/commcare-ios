package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException
import kotlin.math.min

open class XPathTranslateFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return translate(evaluatedArgs[0], evaluatedArgs[1], evaluatedArgs[2])
    }

    companion object {
        const val NAME: String = "translate"
        private const val EXPECTED_ARG_COUNT: Int = 3

        /**
         * Replace each of a given set of characters with another set of characters.
         * If the characters to replace are "abc" and the replacement string is "def",
         * each "a" in the source string will be replaced with "d", each "b" with "e", etc.
         * If a character appears multiple times in the string of characters to replace, the
         * first occurrence is the one that will be used.
         *
         * Any extra characters in the string of characters to replace will be deleted from the source.
         * Any extra characters in the string of replacement characters will be ignored.
         *
         * @param o1 String to manipulate
         * @param o2 String of characters to replace
         * @param o3 String of replacement characters
         */
        private fun translate(o1: Any?, o2: Any?, o3: Any?): String {
            val source = FunctionUtils.toString(o1)
            val from = FunctionUtils.toString(o2)
            val to = FunctionUtils.toString(o3)

            val map = HashMap<Char, Char>()
            for (i in 0 until min(from.length, to.length)) {
                if (!map.containsKey(from[i])) {
                    map[from[i]] = to[i]
                }
            }
            val toDelete = from.substring(min(from.length, to.length))

            var returnValue = ""
            for (i in 0 until source.length) {
                var current = source[i]
                if (toDelete.indexOf(current) == -1) {
                    if (map.containsKey(current)) {
                        current = map[current]!!
                    }
                    returnValue += current
                }
            }

            return returnValue
        }
    }
}
