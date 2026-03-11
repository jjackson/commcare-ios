package org.commcare.core.graph.c3

import org.commcare.core.graph.model.GraphData
import org.json.JSONException
import org.json.JSONObject

/**
 * Grid-related configuration for C3.
 *
 * Created by jschweers on 11/16/2015.
 */
class GridConfiguration(data: GraphData) : Configuration(data) {
    init {
        val showGrid = java.lang.Boolean.valueOf(mData.getConfiguration("show-grid", "true"))
        if (showGrid) {
            val show = JSONObject("{ show: true }")
            mConfiguration.put("x", show)
            mConfiguration.put("y", show)
        }
    }
}
