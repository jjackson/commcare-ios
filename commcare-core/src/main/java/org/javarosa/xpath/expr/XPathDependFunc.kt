package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.parser.XPathSyntaxException

// non-standard
open class XPathDependFunc : XPathFuncExpr, VolatileXPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 1) {
            throw XPathArityException(name, "at least one argument", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return evaluatedArgs[0]!!
    }

    override fun rootExpressionTypeIsCacheable(): Boolean {
        return false
    }

    companion object {
        const val NAME: String = "depend"
        // at least one argument
        private const val EXPECTED_ARG_COUNT: Int = -1
    }
}
