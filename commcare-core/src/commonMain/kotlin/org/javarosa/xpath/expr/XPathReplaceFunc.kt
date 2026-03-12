package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathReplaceFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return replace(evaluatedArgs[0], evaluatedArgs[1], evaluatedArgs[2])
    }

    companion object {
        const val NAME: String = "replace"
        private const val EXPECTED_ARG_COUNT: Int = 3

        /**
         * Regex-based replacement.
         *
         * @param o1 String to manipulate
         * @param o2 Pattern to search for
         * @param o3 Replacement string. Contrary to the XPath spec, this function does NOT
         *           support backreferences (e.g., replace("abbc", "a(.*)c", "$1") will return "a$1c", not "bb").
         * @return String
         */
        private fun replace(o1: Any?, o2: Any?, o3: Any?): String {
            val source = FunctionUtils.toString(o1)
            val regexString = FunctionUtils.toString(o2)
            val replacement = FunctionUtils.toString(o3)
            try {
                return source.replace(Regex(regexString), Regex.escapeReplacement(replacement))
            } catch (e: IllegalArgumentException) {
                throw XPathException("The regular expression '$regexString' is invalid.")
            }
        }
    }
}
