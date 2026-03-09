package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathLazyNodeset
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathPositionFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size > 1) {
            throw XPathArityException(name, "0 or one arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        if (args.size == 1) {
            val expr = evaluatedArgs[0] as XPathNodeset
            try {
                return position(expr.getRefAt(0))
            } catch (e: ArrayIndexOutOfBoundsException) {
                if (expr is XPathLazyNodeset) {
                    throw XPathTypeMismatchException("Unable to evaluate `position` on " + expr.getUnexpandedRefString() + ", which is empty.")
                } else {
                    throw XPathTypeMismatchException("Unable to evaluate `position` on empty reference in the context of " + evalContext.contextRef)
                }
            }
        } else if (evalContext.getContextPosition() != -1) {
            return java.lang.Double.valueOf(evalContext.getContextPosition().toDouble())
        } else {
            return position(evalContext.contextRef!!)
        }
    }

    companion object {
        const val NAME: String = "position"
        // 0 or 1 arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        private fun position(refAt: TreeReference): Double {
            return java.lang.Double.valueOf(refAt.getMultLast().toDouble())
        }
    }
}
