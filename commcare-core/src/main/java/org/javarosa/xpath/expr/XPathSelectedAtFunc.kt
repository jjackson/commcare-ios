package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.DataUtil
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathSelectedAtFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return selectedAt(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "selected-at"
        private const val EXPECTED_ARG_COUNT: Int = 2

        /**
         * Get the Nth item in a selected list
         *
         * @param o1 XML-serialized answer to multi-select question (i.e, space-delimited choice values)
         * @param o2 the integer index into the list to return
         */
        private fun selectedAt(o1: Any?, o2: Any?): String {
            val selection = FunctionUtils.unpack(o1) as String
            val index = FunctionUtils.toInt(o2).toInt()

            val entries = DataUtil.splitOnSpaces(selection)

            if (entries.size <= index) {
                throw XPathException(
                    "Attempting to select element " + index +
                            " of a list with only " + entries.size + " elements."
                )
            } else {
                return entries[index]
            }
        }
    }
}
