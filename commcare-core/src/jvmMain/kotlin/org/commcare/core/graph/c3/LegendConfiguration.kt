package org.commcare.core.graph.c3

import org.commcare.core.graph.model.GraphData
import org.json.JSONException

/**
 * Legend-related configuration for C3.
 *
 * Created by jschweers on 11/16/2015.
 */
class LegendConfiguration(data: GraphData) : Configuration(data) {
    init {
        // Respect user's preference for showing legend
        val showLegend = java.lang.Boolean.valueOf(mData.getConfiguration("show-legend", "false"))
        if (!showLegend) {
            mConfiguration.put("show", false)
        }
    }
}
