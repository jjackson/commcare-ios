package org.javarosa.xpath.expr

import org.gavaghan.geodesy.GlobalCoordinates
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.utils.GeoPointUtils
import org.javarosa.core.model.utils.PolygonUtils
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException
import java.util.Arrays

/**
 * XPath function "is-point-inside-polygon()" determines whether a geographic point lies inside
 * or on the boundary of a polygon.
 */
open class XPathIsPointInsidePolygonFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return isPointWithinBoundary(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "is-point-inside-polygon"
        private const val EXPECTED_ARG_COUNT: Int = 2

        private fun isPointWithinBoundary(from: Any?, to: Any?): Boolean {
            val inputPoint = FunctionUtils.unpack(from) as String?
            val inputPolygon = FunctionUtils.unpack(to) as String?
            if (inputPoint == null || "" == inputPoint || inputPolygon == null || "" == inputPolygon) {
                throw XPathException(
                    "is-point-inside-polygon() function requires coordinates of point and polygon"
                )
            }
            try {
                val coordinates = inputPolygon.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val polygon: List<GlobalCoordinates> = PolygonUtils.createPolygon(Arrays.asList(*coordinates))
                val pointData = GeoPointData().cast(UncastData(inputPoint))
                GeoPointUtils.validateCoordinates(pointData.getLatitude(), pointData.getLongitude())
                val pointCoordinates = GlobalCoordinates(pointData.getLatitude(), pointData.getLongitude())
                return PolygonUtils.isPointInsideOrOnPolygon(pointCoordinates, polygon)
            } catch (e: NumberFormatException) {
                throw XPathTypeMismatchException(
                    "is-point-inside-polygon() function requires arguments containing " +
                            "numeric values only, but received arguments: " + inputPoint + " and " + inputPolygon
                )
            } catch (e: IllegalArgumentException) {
                throw XPathException(e.message)
            }
        }
    }
}
