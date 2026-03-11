package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.utils.GeoPointUtils
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathDistanceFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return distance(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "distance"
        private const val EXPECTED_ARG_COUNT: Int = 2

        /**
         * Returns the distance between two GeoPointData locations, in meters, given objects to unpack.
         * Ignores altitude and accuracy.
         * Note that the arguments can be strings.
         * Returns -1 if one of the arguments is null or the empty string.
         */
        fun distance(from: Any?, to: Any?): Double {
            val unpackedFrom = FunctionUtils.unpack(from) as String?
            val unpackedTo = FunctionUtils.unpack(to) as String?

            if (unpackedFrom == null || "" == unpackedFrom || unpackedTo == null || "" == unpackedTo) {
                return java.lang.Double.valueOf(-1.0)
            }

            try {
                // Casting and uncasting seems strange but is consistent with the codebase
                val castedFrom = GeoPointData().cast(UncastData(unpackedFrom))
                val castedTo = GeoPointData().cast(UncastData(unpackedTo))

                return java.lang.Double.valueOf(GeoPointUtils.computeDistanceBetween(castedFrom, castedTo))
            } catch (e: NumberFormatException) {
                throw XPathTypeMismatchException(
                    "distance() function requires arguments containing " +
                            "numeric values only, but received arguments: " + unpackedFrom + " and " + unpackedTo
                )
            }
        }
    }
}
