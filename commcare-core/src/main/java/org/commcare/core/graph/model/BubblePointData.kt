package org.commcare.core.graph.model

/**
 * Representation of a point on a bubble chart, which has an x, y position
 * and an additional value for the bubble's radius.
 *
 * @author jschweers
 */
class BubblePointData(
    x: String?,
    y: String?,
    private val mRadius: String?
) : XYPointData(x, y) {

    fun getRadius(): String? = mRadius
}
