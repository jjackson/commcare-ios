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
 * XPath function "closest-point-on-polygon()" computes the closest point on the boundary of a polygon
 * to a given geographic point.
 */
open class XPathClosestPointOnPolygonFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    /**
     * Returns the point on polygon closest to the geopoint, in "Lat Lng", given objects to unpack.
     * Ignores altitude and accuracy.
     * Note that the arguments can be strings.
     * Returns "" if one of the arguments is null or the empty string.
     */
    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return closestPointToPolygon(evaluatedArgs[0], evaluatedArgs[1])
    }

    companion object {
        const val NAME: String = "closest-point-on-polygon"
        private const val EXPECTED_ARG_COUNT: Int = 2

        private fun closestPointToPolygon(from: Any?, to: Any?): String {
            val inputPoint = FunctionUtils.unpack(from) as String?
            val inputPolygon = FunctionUtils.unpack(to) as String?
            if (inputPoint == null || "" == inputPoint || inputPolygon == null || "" == inputPolygon) {
                throw XPathException(
                    "closest-point-on-polygon() function requires coordinates of point and polygon"
                )
            }
            try {
                val coordinates = inputPolygon.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val polygon: List<GlobalCoordinates> = PolygonUtils.createPolygon(Arrays.asList(*coordinates))
                val pointData = GeoPointData().cast(UncastData(inputPoint))
                GeoPointUtils.validateCoordinates(pointData.latitude, pointData.longitude)
                val pointCoordinates = GlobalCoordinates(pointData.latitude, pointData.longitude)
                return PolygonUtils.findClosestPoint(pointCoordinates, polygon).toString()
            } catch (e: NumberFormatException) {
                throw XPathTypeMismatchException(
                    "closest-point-on-polygon() function requires arguments containing " +
                            "numeric values only, but received arguments: " + inputPoint + " and " + inputPolygon
                )
            } catch (e: IllegalArgumentException) {
                throw XPathException(e.message)
            }
        }
    }
}
