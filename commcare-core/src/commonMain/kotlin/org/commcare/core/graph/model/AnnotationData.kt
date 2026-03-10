package org.commcare.core.graph.model

/**
 * Data for an annotation, which is text drawn at a specified x, y coordinate on a graph.
 *
 * @author jschweers
 */
class AnnotationData(
    x: String?,
    y: String?,
    private val mAnnotation: String
) : XYPointData(x, y) {

    fun getAnnotation(): String = mAnnotation
}
