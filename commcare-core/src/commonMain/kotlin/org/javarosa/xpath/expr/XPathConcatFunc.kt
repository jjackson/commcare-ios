package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathConcatFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return if (args.size == 1 && evaluatedArgs[0] is XPathNodeset) {
            XPathJoinFunc.join("", (evaluatedArgs[0] as XPathNodeset).toArgList())
        } else {
            XPathJoinFunc.join("", evaluatedArgs)
        }
    }

    companion object {
        const val NAME: String = "concat"
        // zero or more arguments
        private const val EXPECTED_ARG_COUNT: Int = -1
    }
}
