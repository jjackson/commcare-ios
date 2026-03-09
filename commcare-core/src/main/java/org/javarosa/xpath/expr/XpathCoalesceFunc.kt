package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XpathCoalesceFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 1) {
            throw XPathArityException(name, "1 or more arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        // Not sure if unpacking here is quiiite right, but it seems right
        for (i in 0 until args.size - 1) {
            val evaluatedArg = FunctionUtils.unpack(args[i].eval(model, evalContext))
            if (!isNull(evaluatedArg)) {
                return evaluatedArg!!
            }
        }
        return args[args.size - 1].eval(model, evalContext)
    }

    companion object {
        const val NAME: String = "coalesce"
        // at least 1 argument
        private const val EXPECTED_ARG_COUNT: Int = -1

        private fun isNull(o: Any?): Boolean {
            if (o == null) {
                return true //true 'null' values aren't allowed in the xpath engine, but whatever
            } else if (o is String && o.length == 0) {
                return true
            } else {
                return o is Double && o.isNaN()
            }
        }
    }
}
