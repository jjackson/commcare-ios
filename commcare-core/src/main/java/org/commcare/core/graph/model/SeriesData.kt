package org.commcare.core.graph.model


/**
 * Contains the fully-evaluated data for a single graph series.
 *
 * @author jschweers
 */
class SeriesData : ConfigurableData {
    private val mPoints = ArrayList<XYPointData>()
    private val mConfiguration = HashMap<String, String>()

    fun addPoint(p: XYPointData) {
        mPoints.add(p)
    }

    fun getPoints(): ArrayList<XYPointData> = mPoints

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
