package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException

// non-standard
open class XPathSelectedFunc : XPathFuncExpr {
    constructor() {
        // default to 'selected' but could be 'is-selected'
        // we could also serialize this if we wanted to really preserve it.
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(name: String, args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true) {
        // keep function name from parsing instead of using default
        this.name = name
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return multiSelected(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "selected"
        private const val EXPECTED_ARG_COUNT: Int = 2

        /**
         * return whether a particular choice of a multi-select is selected
         *
         * @param o1 XML-serialized answer to multi-select question (i.e, space-delimited choice values)
         * @param o2 choice to look for
         */
        private fun multiSelected(o1: Any?, o2: Any?): Boolean {
            var unpacked1 = FunctionUtils.unpack(o1)
            if (unpacked1 !is String) {
                throw generateBadArgumentMessage("selected", 1, "multi-select question", unpacked1)
            }

            var unpacked2 = FunctionUtils.unpack(o2)
            if (unpacked2 !is String) {
                throw generateBadArgumentMessage("selected", 2, "single potential value from the list of select options", unpacked2)
            }

            val s1 = unpacked1 as String
            val s2 = (unpacked2 as String).trim()

            return (" $s1 ").contains(" $s2 ")
        }

        private fun generateBadArgumentMessage(functionName: String, argNumber: Int, type: String, endValue: Any?): XPathException {
            return XPathException("Bad argument to function '$functionName'. Argument #$argNumber should be a $type, but instead evaluated to: ${endValue.toString()}")
        }
    }
}
