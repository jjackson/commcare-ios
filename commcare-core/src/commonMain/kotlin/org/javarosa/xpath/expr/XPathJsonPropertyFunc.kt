package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException
import org.commcare.util.jsonGetString

/**
 * Utility for hidden values as geocoder receivers
 *
 * @author rcostello
 * @return A String value for the property name passed in if that property exists else a blank String
 */
open class XPathJsonPropertyFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return getJsonProperty(FunctionUtils.toString(evaluatedArgs[0]), FunctionUtils.toString(evaluatedArgs[1]))
    }

    companion object {
        const val NAME: String = "json-property"
        private const val EXPECTED_ARG_COUNT: Int = 2

        /**
         * Returns the value of the property name passed in from the stringified json object passed in.
         * Returns a blank string if the property does not exist on the stringified json object.
         */
        fun getJsonProperty(stringifiedJsonObject: String, propertyName: String): String {
            return jsonGetString(stringifiedJsonObject, propertyName) ?: ""
        }
    }
}
