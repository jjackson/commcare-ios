package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.PropertyUtils
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathUuidFunc : XPathFuncExpr, VolatileXPathFuncExpr {
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

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        //calculated expressions may be recomputed w/o warning! use with caution!!
        if (args.size == 0) {
            return PropertyUtils.genUUID()
        }

        val len = FunctionUtils.toInt(evaluatedArgs[0]).toInt()
        return PropertyUtils.genGUID(len)
    }

    override fun rootExpressionTypeIsCacheable(): Boolean {
        return false
    }

    companion object {
        const val NAME: String = "uuid"
        // 0 or 1 arguments
        private const val EXPECTED_ARG_COUNT: Int = -1
    }
}
