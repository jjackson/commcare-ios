package org.commcare.core.graph.model

import java.util.Hashtable
import java.util.Vector

/**
 * Contains the fully-evaluated data for a single graph series.
 *
 * @author jschweers
 */
class SeriesData : ConfigurableData {
    private val mPoints = Vector<XYPointData>()
    private val mConfiguration = Hashtable<String, String>()

    fun addPoint(p: XYPointData) {
        mPoints.addElement(p)
    }

    fun getPoints(): Vector<XYPointData> = mPoints

    /**
     * Number of points in the series.
     */
    fun size(): Int = mPoints.size

    override fun setConfiguration(key: String, value: String) {
        mConfiguration[key] = value
    }

    override fun getConfiguration(key: String): String? = mConfiguration[key]

    override fun getConfiguration(key: String, defaultValue: String): String {
        return getConfiguration(key) ?: defaultValue
    }
}
