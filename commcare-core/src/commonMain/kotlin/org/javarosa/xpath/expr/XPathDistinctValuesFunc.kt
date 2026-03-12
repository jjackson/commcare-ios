package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

/**
 * Implements distinct-values against a nodeset input.
 *
 * Will return a sequence with no duplicate values. Note that sequences are only currently
 * supported provisionally by a limited number of methods.
 *
 * This method will currently _not_ perform type inference in a meaningful way. Values are compared
 * through simple string equality in their current form.
 *
 * Created by ctsims on 11/14/2017.
 */
open class XPathDistinctValuesFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        val argList = FunctionUtils.getSequence(evaluatedArgs[0])

        val returnSet = LinkedHashSet<String>()
        for (o in argList) {
            returnSet.add(FunctionUtils.toString(o))
        }
        return returnSet.toTypedArray()
    }

    companion object {
        const val NAME: String = "distinct-values"
        // one or more arguments
        private const val EXPECTED_ARG_COUNT: Int = 1
    }
}
