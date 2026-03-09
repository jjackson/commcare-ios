package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

/**
 * Conditional function that is an alternative to nested if-statements
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
open class XPathCondFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, false)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 3) {
            throw XPathSyntaxException(name + "() function requires at least 3 arguments. " + args.size + " arguments provided.")
        } else if (args.size % 2 != 1) {
            throw XPathSyntaxException(name + "() function requires an odd number of arguments. " + args.size + " arguments provided.")
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        var i = 0
        while (i < args.size - 2) {
            if (FunctionUtils.toBoolean(args[i].eval(model, evalContext))) {
                return args[i + 1].eval(model, evalContext)
            }
            i += 2
        }

        return args[args.size - 1].eval(model, evalContext)
    }

    companion object {
        const val NAME: String = "cond"
        // expects at least 3 arguments
        private const val EXPECTED_ARG_COUNT: Int = -1
    }
}
