package org.commcare.core.graph.c3
import java.util.TreeMap

import org.commcare.core.graph.model.GraphData
import org.commcare.core.graph.util.GraphException
import org.commcare.core.graph.util.GraphUtil
import org.json.JSONObject
import java.text.SimpleDateFormat
import org.javarosa.core.model.utils.PlatformDate
import java.util.SortedMap

/**
 * Base class for helper classes that build C3 graph configuration.
 * This class itself is not meant to be instantiated. For subclasses,
 * the bulk of the work is done in the constructor. The instantiator
 * can then call getConfiguration and getVariables to get at the JSON
 * configuration and any JavaScript variables that configuration depends on.
 *
 * Created by jschweers on 11/16/2015.
 */
open class Configuration(data: GraphData) {
    private val mDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    internal val mData: GraphData = data
    internal val mConfiguration: JSONObject = JSONObject()
    internal val mVariables: SortedMap<String, String> = TreeMap()
    internal val isRotatedBarGraph: Boolean = mData.getType() == GraphUtil.TYPE_BAR &&
            !mData.getConfiguration("bar-orientation", "horizontal").equals("vertical", ignoreCase = true)

    fun getConfiguration(): JSONObject = mConfiguration

    fun getVariables(): SortedMap<String, String> = mVariables

    /**
     * Parse given time value into string acceptable to C3.
     *
     * @param value       The value, which may be a YYYY-MM-DD string, a YYYY-MM-DD HH:MM:SS,
     *                    or a double representing days since the epoch.
     * @param description Something to identify the kind of value, used to augment any error message.
     * @return String of format YYYY-MM-DD HH:MM:SS, which is what C3 expects.
     * This expected format is set in DataConfiguration as xFormat.
     * @throws GraphException
     */
    @Throws(GraphException::class)
    internal fun parseTime(value: String, description: String): String {
        var v = value
        if (v.matches(Regex(".*[^0-9.].*"))) {
            if (!v.matches(Regex(".*:.*"))) {
                v += " 00:00:00"
            }
        } else {
            val daysSinceEpoch = parseDouble(v, description)
            val d = PlatformDate((daysSinceEpoch * 86400000L).toLong())
            v = mDateFormat.format(d)
        }
        return v
    }

    /**
     * Attempt to parse a double, but fail on NumberFormatException.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    @Throws(GraphException::class)
    internal fun parseDouble(value: String, description: String): Double {
        try {
            val numeric = java.lang.Double.valueOf(value)
            if (numeric.isNaN()) {
                throw GraphException("Could not understand '$value' in $description")
            }
            return numeric
        } catch (nfe: NumberFormatException) {
            throw GraphException("Could not understand '$value' in $description")
        }
    }
}
