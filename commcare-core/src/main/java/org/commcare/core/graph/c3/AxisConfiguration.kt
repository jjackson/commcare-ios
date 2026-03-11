package org.commcare.core.graph.c3

import org.commcare.core.graph.model.GraphData
import org.commcare.core.graph.model.SeriesData
import org.commcare.core.graph.util.GraphException
import org.commcare.core.graph.util.GraphUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.round

/**
 * Axis-related configuration for C3.
 *
 * Created by jschweers on 11/16/2015.
 */
class AxisConfiguration(data: GraphData) : Configuration(data) {

    init {
        val x = getAxis("x")
        val y = getAxis("y")
        val y2 = getAxis("secondary-y")

        if (mData.getType() == GraphUtil.TYPE_TIME) {
            x.put("type", "timeseries")
        }

        mConfiguration.put("x", x)
        mConfiguration.put("y", y)
        mConfiguration.put("y2", y2)

        // Bar graphs may be rotated. C3 defaults to vertical bars.
        if (isRotatedBarGraph) {
            mConfiguration.put("rotated", true)
        }
    }

    /**
     * Add min and max bounds to given axis.
     *
     * @param axis   Current axis configuration. Will be modified.
     * @param prefix Prefix for commcare model's configuration: "x", "y", or "secondary-y"
     */
    @Throws(GraphException::class, JSONException::class)
    private fun addBounds(axis: JSONObject, prefix: String) {
        addBound(axis, prefix, "min")
        addBound(axis, prefix, "max")
    }

    /**
     * Add min or max bound to given axis.
     *
     * @param axis   Current axis configuration. Will be modified.
     * @param prefix Prefix for commcare model's configuration: "x", "y", or "secondary-y"
     * @param suffix "min" or "max"
     */
    @Throws(GraphException::class, JSONException::class)
    private fun addBound(axis: JSONObject, prefix: String, suffix: String) {
        val key = "$prefix-$suffix"
        val value = mData.getConfiguration(key)
        if (value != null) {
            if (prefix == "x" && mData.getType() == GraphUtil.TYPE_TIME) {
                axis.put(suffix, parseTime(value, key))
            } else {
                axis.put(suffix, parseDouble(value, key))
            }
        }
    }

    /**
     * Configure tick count, placement, and labels.
     *
     * @param axis    Current axis configuration. Will be modified.
     * @param key     One of "x-labels", "y-labels", "secondary-y-labels"
     * @param varName If the axis uses a hash of labels (position => label), a variable
     *                will be created with this name to store those labels.
     */
    @Throws(GraphException::class, JSONException::class)
    private fun addTickConfig(axis: JSONObject, key: String, varName: String) {
        // The labels configuration might be a JSON array of numbers,
        // a JSON object of number => string, or a single number
        val labelString = mData.getConfiguration(key)
        val tick = JSONObject()
        var usingCustomText = false
        val isX = key.startsWith("x")

        mVariables[varName] = "{}"
        if (labelString != null) {
            try {
                // Array: label each given value
                val labels = JSONArray(labelString)
                val values = JSONArray()
                for (i in 0 until labels.length()) {
                    val value = labels.get(i).toString()
                    if (isX && mData.getType() == GraphUtil.TYPE_TIME) {
                        values.put(parseTime(value, key))
                    } else {
                        values.put(parseDouble(value, key))
                    }
                }
                tick.put("values", values)
            } catch (je: JSONException) {
                // Assume try block failed because labelString isn't an array.
                // Try parsing it as an object.
                try {
                    // Object: each key is a location on the axis,
                    // and the value is text with which to label it
                    val labels = JSONObject(labelString)
                    val values = JSONArray()
                    var largestLabel = ""
                    val i = labels.keys().iterator()
                    while (i.hasNext()) {
                        val location = i.next() as String
                        if (isX && mData.getType() == GraphUtil.TYPE_TIME) {
                            values.put(parseTime(location, key))
                        } else {
                            values.put(parseDouble(location, key))
                        }
                        try {
                            val current = labels.getString(location)
                            if (current.length > largestLabel.length) {
                                largestLabel = current
                            }
                        } catch (e: JSONException) {
                            // ignore
                        }
                    }
                    tick.put("values", values)
                    mVariables[varName] = labels.toString()
                    usingCustomText = true
                } catch (e: JSONException) {
                    // Assume labelString is just a scalar, which
                    // represents the number of labels the user wants.
                    tick.put("count", kotlin.math.round(labelString.toDouble()))
                }
            }
        }

        if (isX && !usingCustomText && mData.getType() == GraphUtil.TYPE_TIME) {
            tick.put("format", mData.getConfiguration("x-labels-time-format", "%Y-%m-%d"))
        }

        if (key.startsWith("secondary-y")) {
            // If there aren't any series for the secondary y axis, don't label it
            var hasSecondaryAxis = false
            for (s in mData.getSeries()) {
                hasSecondaryAxis = hasSecondaryAxis || java.lang.Boolean.valueOf(s.getConfiguration("secondary-y", "false"))
                if (hasSecondaryAxis) {
                    break
                }
            }
            if (!hasSecondaryAxis) {
                tick.put("values", JSONArray())
            }
        }

        if ((isX && !isRotatedBarGraph) || (isRotatedBarGraph && key.startsWith("y"))) {
            tick.put("rotate", 75)
        }
        if (tick.length() > 0) {
            axis.put("tick", tick)
        }
    }

    /**
     * Add title to axis.
     *
     * @param axis     Current axis configuration. Will be modified.
     * @param key      One of "x-title", "y-title", "secondary-y-title"
     * @param position For horizontal axis, (inner|outer)-(right|center|left)
     *                 For vertical axis, (inner|outer)-(top|middle|bottom)
     */
    @Throws(JSONException::class)
    private fun addTitle(axis: JSONObject, key: String, position: String) {
        var title = mData.getConfiguration(key, "")

        // String.trim doesn't cover characters like unicode's non-breaking space
        title = title.replace(Regex("^\\s*"), "")
        title = title.replace(Regex("\\s*$"), "")

        // Show title regardless of whether or not it exists, to give all graphs consistent padding
        val label = JSONObject()
        label.put("text", title)
        label.put("position", position)
        axis.put("label", label)
    }

    /**
     * Generate axis configuration.
     *
     * @param prefix Prefix for commcare model's configuration: "x", "y", or "secondary-y"
     * @return JSONObject representing the axis's configuration
     */
    @Throws(GraphException::class, JSONException::class)
    private fun getAxis(prefix: String): JSONObject {
        val showAxes = java.lang.Boolean.valueOf(mData.getConfiguration("show-axes", "true"))
        if (!showAxes) {
            return JSONObject("{ show: false }")
        }

        val config = JSONObject()
        val isX = prefix == "x"

        // X and primary Y axis show by default, but not secondary y. Force them all to show.
        // Display secondary y axis, regardless of if it has data; this makes the
        // whitespace around the graph look more reasonable.
        config.put("show", true)

        // Undo C3's automatic axis padding
        config.put("padding", JSONObject("{top: 0, right: 0, bottom: 0, left: 0}"))

        if (isRotatedBarGraph && prefix.startsWith("y")) {
            addTitle(config, "$prefix-title", "inner-right")
        } else {
            addTitle(config, "$prefix-title", if (isX) "outer-center" else "outer-middle")
        }

        addBounds(config, prefix)

        val jsPrefix = if (prefix == "secondary-y") "y2" else prefix
        addTickConfig(config, "$prefix-labels", "${jsPrefix}Labels")

        return config
    }
}
