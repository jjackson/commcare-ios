package org.commcare.core.graph.model

/**
 * Representation of a point on an x, y plane.
 *
 * @author jschweers
 */
open class XYPointData(
    private val mX: String?,
    private val mY: String?
) {
    fun getX(): String? = mX
    fun getY(): String? = mY
}
