package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathIfFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, false)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size != expectedArgCount) {
            val msg = name + "() function requires " +
                    expectedArgCount + " arguments but " +
                    args.size + " are present."
            throw XPathSyntaxException(msg)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return if (FunctionUtils.toBoolean(args[0].eval(model, evalContext))) {
            args[1].eval(model, evalContext)
        } else {
            args[2].eval(model, evalContext)
        }
    }

    companion object {
        const val NAME: String = "if"
        private const val EXPECTED_ARG_COUNT: Int = 3
    }
}
