package org.commcare.core.graph.c3

import org.commcare.core.graph.model.AnnotationData
import org.commcare.core.graph.model.BubblePointData
import org.commcare.core.graph.model.GraphData
import org.commcare.core.graph.model.SeriesData
import org.commcare.core.graph.model.XYPointData
import org.commcare.core.graph.util.ColorUtils
import org.commcare.core.graph.util.GraphException
import org.commcare.core.graph.util.GraphUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max

/**
 * Data-related configuration for C3. This configuration should be run before
 * any others, as the data will sometimes affect other configuration.
 *
 * Created by jschweers on 11/16/2015.
 */
class DataConfiguration(data: GraphData) : Configuration(data) {
    // Actual data: array of arrays, where first element is a string id
    // and later elements are data, either x values or y values.
    private val mColumns = JSONArray()

    // Hash that pairs up the arrays defined in columns,
    // y-values-array-id => x-values-array-id
    private val mXs = JSONObject()

    // Hash of y-values id => name for legend
    private val mNames = JSONObject()
    private val mXNames = JSONObject()

    // Hash of y-values id => 'y' or 'y2' depending on whether this data
    // should be plotted against the primary or secondary y axis
    private val mAxes = JSONObject()

    // Hash of y-values id => line, scatter, bar, area, etc.
    private val mTypes = JSONObject()

    // Hash of y-values id => series color
    private val mColors = JSONObject()
    private val mLineOpacities = JSONObject()
    private val mAreaColors = JSONObject()
    private val mAreaOpacities = JSONObject()

    // Array of series that should appear in legend & tooltip
    private val mIsData = JSONObject()

    // Hash of y-values id => point-style string ("circle", "none", "cross", etc.)
    // Doubles as a record of all user-defined series
    // (as opposed to series for annotations, etc.)
    private val mPointStyles = JSONObject()

    // Bar graph data:
    //  mBarCount: for the sake of setting x min and max
    //  mBarLabels: the actual labels to display, which are supposed to be the same
    //      for every series, hence the booleans so we only record them once
    //  mBarColors: hash of y-values id => array of colors, with one color for each bar
    //  mBarOpacities: analogous to mBarColors, but for bar opacity values
    private var mBarCount = 0
    private val mBarLabels = JSONArray("['']")
    private val mBarColors = JSONObject()
    private val mBarOpacities = JSONObject()

    // Bubble graph data:
    //  y-values id => array of radius values
    //  y-values id => max radius found in that data (or specified by max-radius param)
    private val mRadii = JSONObject()
    private val mMaxRadii = JSONObject()

    init {
        // Process data for each series
        var seriesIndex = 0
        for (s in mData.getSeries()) {
            val xID = "x$seriesIndex"
            val yID = "y$seriesIndex"
            mXs.put(yID, xID)

            setColumns(xID, yID, s)
            setColor(yID, s)
            setName(yID, s)
            setIsData(yID, s)
            setPointStyle(yID, s)
            setType(yID, s)
            setYAxis(yID, s)

            seriesIndex++
        }

        // Set up separate variables for features that C3 doesn't support well
        mVariables["areaColors"] = mAreaColors.toString()
        mVariables["areaOpacities"] = mAreaOpacities.toString()
        mVariables["barColors"] = mBarColors.toString()
        mVariables["barOpacities"] = mBarOpacities.toString()
        mVariables["isData"] = mIsData.toString()
        mVariables["lineOpacities"] = mLineOpacities.toString()
        mVariables["maxRadii"] = mMaxRadii.toString()
        mVariables["pointStyles"] = mPointStyles.toString()
        mVariables["radii"] = mRadii.toString()
        mVariables["xNames"] = mXNames.toString()

        // Data-based tweaking of user's configuration and adding system series
        normalizeBoundaries()
        addAnnotations()
        addBoundaries()

        // Type-specific logic
        if (mData.getType() == GraphUtil.TYPE_TIME) {
            mConfiguration.put("xFormat", "%Y-%m-%d %H:%M:%S")
        }

        // Whether or not to show data labels at each point/bar
        val showLabels = java.lang.Boolean.valueOf(mData.getConfiguration("show-data-labels", "false"))
        if (showLabels) {
            mConfiguration.put("labels", true)
        }

        // Finally, apply all data to main configuration
        mConfiguration.put("axes", mAxes)
        mConfiguration.put("colors", mColors)
        mConfiguration.put("columns", mColumns)
        mConfiguration.put("names", mNames)
        mConfiguration.put("types", mTypes)
        mConfiguration.put("xs", mXs)
        mConfiguration.put("groups", getGroups())
    }

    /**
     * Add annotations, by creating a fake series with data labels turned on.
     */
    @Throws(GraphException::class, JSONException::class)
    private fun addAnnotations() {
        val text = JSONObject()

        var index = 0
        for (a in mData.getAnnotations()) {
            val xID = "annotationsX$index"
            val yID = "annotationsY$index"
            val description = "annotation '${a.getAnnotation()}' at (${a.getX()}, ${a.getY()})"
            text.put(yID, a.getAnnotation())

            // Add x value
            val xValues = JSONArray()
            xValues.put(xID)
            if (mData.getType() == GraphUtil.TYPE_TIME) {
                xValues.put(parseTime(a.getX()!!, description))
            } else {
                xValues.put(parseDouble(a.getX()!!, description))
            }
            mColumns.put(xValues)

            // Add y value
            val yValues = JSONArray()
            yValues.put(yID)
            yValues.put(parseDouble(a.getY()!!, description))
            mColumns.put(yValues)

            // Configure series
            mXs.put(yID, xID)
            mTypes.put(yID, "line")
            mAxes.put(yID, "y")

            index++
        }

        mVariables["annotations"] = text.toString()
    }

    /**
     * Create fake series so there's data all the way to the edges of the user-specified
     * min and max. C3 does tick placement in part based on data, so this will force
     * it to place ticks based on the user's desired min/max range.
     */
    @Throws(GraphException::class, JSONException::class)
    private fun addBoundaries() {
        val xMin = mData.getConfiguration("x-min")
        val xMax = mData.getConfiguration("x-max")

        // If we don't have user-specified bounds, don't bother.
        if (xMin == null || xMax == null) {
            return
        }

        val xID = "boundsX"
        if (addBoundary(xID, "boundsY", "y") || addBoundary(xID, "boundsY2", "secondary-y")) {
            // If at least one y axis had boundaries and therefore a series was created,
            // now create the matching x values
            val xValues = JSONArray()
            xValues.put(xID)
            if (mData.getType() == GraphUtil.TYPE_TIME) {
                xValues.put(parseTime(xMin, "x-min"))
                xValues.put(parseTime(xMax, "x-max"))
            } else {
                xValues.put(parseDouble(xMin, "x-min"))
                xValues.put(parseDouble(xMax, "x-max"))
            }
            mColumns.put(xValues)
        }
    }

    /**
     * Helper for addBoundaries: possibly add a series for either the primary or secondary y axis,
     * depending on whether or not that axis has a min and max specified.
     *
     * @param xID    ID of x column to associate with the new series
     * @param yID    ID of y column for new series
     * @param prefix "y" or "secondary-y"
     * @return True iff a series was actually created
     */
    @Throws(GraphException::class, JSONException::class)
    private fun addBoundary(xID: String, yID: String, prefix: String): Boolean {
        val min = mData.getConfiguration("$prefix-min")
        val max = mData.getConfiguration("$prefix-max")
        if (min != null && max != null) {
            mXs.put(yID, xID)
            mTypes.put(yID, "line")
            mAxes.put(yID, if (prefix.startsWith("secondary")) "y2" else "y")

            val yValues = JSONArray()
            yValues.put(yID)
            yValues.put(parseDouble(min, "$prefix-min"))
            yValues.put(parseDouble(max, "$prefix-max"))
            mColumns.put(yValues)
            return true
        }
        return false
    }

    /**
     * Set up stacked bar graph, if needed. Expects series data to have
     * already been processed (specifically, expects mTypes to be populated).
     *
     * @return JSONArray of configuration for groups, C3's version of stacking
     */
    @Throws(JSONException::class)
    private fun getGroups(): JSONArray {
        val outer = JSONArray()
        val inner = JSONArray()
        if (mData.getType() == GraphUtil.TYPE_BAR &&
            java.lang.Boolean.valueOf(mData.getConfiguration("stack", "false"))
        ) {
            val i = mTypes.keys().iterator()
            while (i.hasNext()) {
                val key = i.next()
                if (mTypes.get(key) == "bar") {
                    inner.put(key)
                }
            }
        } else {
            val i = mTypes.keys().iterator()
            while (i.hasNext()) {
                val yID = i.next()
                if (mTypes.getString(yID) == "area") {
                    inner.put(yID)
                }
            }
        }

        if (inner.length() > 0) {
            outer.put(inner)
        }
        return outer
    }

    /**
     * For bar charts, set up bar labels and force the x axis min and max so bars are spaced nicely
     */
    @Throws(JSONException::class)
    private fun normalizeBoundaries() {
        if (mData.getType() == GraphUtil.TYPE_BAR) {
            mData.setConfiguration("x-min", "0.5")
            mData.setConfiguration("x-max", (mBarCount + 0.5).toString())
            mBarLabels.put("")
            mVariables["barLabels"] = mBarLabels.toString()

            // Force all labels to show; C3 will hide some labels if it thinks there are too many.
            // Skip the first and last elements, which are just spacers, not bars.
            val xLabels = JSONObject()
            for (i in 1 until mBarLabels.length() - 1) {
                xLabels.put(i.toString(), mBarLabels.get(i))
            }
            mData.setConfiguration("x-labels", xLabels.toString())
        }
    }

    /**
     * Set color for a given series.
     *
     * @param yID ID of y-values array to set color
     * @param s   SeriesData from which to pull color
     */
    @Throws(JSONException::class)
    private fun setColor(yID: String, s: SeriesData) {
        val barColorJSON = s.getConfiguration("bar-color")
        if (barColorJSON != null) {
            val requestedColors = JSONArray(barColorJSON)
            if (requestedColors.length() > 0) {
                val colors = JSONArray()
                val opacities = JSONArray()
                for (i in 0 until requestedColors.length()) {
                    var color = requestedColors.getString(i)
                    color = normalizeColor(color)
                    colors.put(i, "#" + color.substring(3))
                    opacities.put(getOpacity(color))
                }
                mBarColors.put(yID, colors)
                mBarOpacities.put(yID, opacities)
                return
            }
        }

        var color = s.getConfiguration("line-color", "#ff000000")
        color = normalizeColor(color)
        mColors.put(yID, "#" + color.substring(3))
        mLineOpacities.put(yID, getOpacity(color))

        var fillBelow = s.getConfiguration("fill-below")
        if (fillBelow != null) {
            fillBelow = normalizeColor(fillBelow)
            mAreaColors.put(yID, "#" + fillBelow.substring(3))
            mAreaOpacities.put(yID, getOpacity(fillBelow))
        }
    }

    /**
     * Convert color string to expected format.
     *
     * @param color String of format #?(AA)?RRGGBB
     * @return String of format "#AARRGGBB"
     */
    private fun normalizeColor(color: String): String {
        var c = color
        if (c.length % 2 == 0) {
            c = "#$c"
        }
        if (c.length == 7) {
            c = "#ff" + c.substring(1)
        }
        return c
    }

    /**
     * Calculate opacity of given color.
     *
     * @param color ColorUtils in format "#AARRGGBB"
     * @return Opacity, which will be between 0 and 1, inclusive
     */
    private fun getOpacity(color: String): Double {
        return ColorUtils.alpha(ColorUtils.parseColor(color)) / 255.0
    }

    /**
     * Set up data: x, y, and radius values
     *
     * @param xID ID of the x-values array
     * @param yID ID of the y-values array
     * @param s   The SeriesData providing the data
     */
    @Throws(GraphException::class, JSONException::class)
    private fun setColumns(xID: String, yID: String, s: SeriesData) {
        val xValues = JSONArray()
        val yValues = JSONArray()
        xValues.put(xID)
        yValues.put(yID)

        var barIndex = 0
        val addBarLabels = mData.getType() == GraphUtil.TYPE_BAR && mBarLabels.length() == 1
        val rValues = JSONArray()
        var maxRadius = parseDouble(s.getConfiguration("max-radius", "0"), "max-radius")
        for (p in s.getPoints()) {
            val description = "data (${p.getX()}, ${p.getY()})"
            if (mData.getType() == GraphUtil.TYPE_BAR) {
                // In CommCare, bar graphs are specified with x as a set of text labels
                // and y as a set of values. In C3, bar graphs are still basically
                // of XY graphs, with numeric x and y values. Deal with this by
                // assigning an arbitrary, evenly-spaced x value to each bar and then
                // using the user's x values as custom labels.
                xValues.put(barIndex + 1)
                mBarCount = max(mBarCount, barIndex + 1)
                if (addBarLabels) {
                    mBarLabels.put(p.getX())
                }
            } else {
                if (mData.getType() == GraphUtil.TYPE_TIME) {
                    val time = parseTime(p.getX()!!, description) // c3 needs YYYY-MM-DD HH:MM:SS format
                    xValues.put(time)
                } else {
                    val `val` = parseDouble(p.getX()!!, description)
                    xValues.put(`val`)
                }
            }
            yValues.put(parseDouble(p.getY()!!, description))

            // Bubble charts also get a radius
            if (mData.getType() == GraphUtil.TYPE_BUBBLE) {
                val b = p as BubblePointData
                val r = parseDouble(b.getRadius()!!, "$description with radius ${b.getRadius()}")
                rValues.put(r)
                maxRadius = max(maxRadius, r)
            }

            barIndex++
        }
        mColumns.put(xValues)
        mColumns.put(yValues)
        if (mData.getType() == GraphUtil.TYPE_BUBBLE) {
            mRadii.put(yID, rValues)
            mMaxRadii.put(yID, maxRadius)
        }
    }

    /**
     * Set whether or not point should appear in legend and tooltip.
     *
     * @param yID ID of y-values array that is or isn't data
     * @param s   SeriesData from which to pull flag
     */
    @Throws(JSONException::class)
    private fun setIsData(yID: String, s: SeriesData) {
        val isData = java.lang.Boolean.valueOf(s.getConfiguration("is-data", "true"))
        if (isData) {
            mIsData.put(yID, 1)
        }
    }

    /**
     * Set series name to display in legend.
     *
     * @param yID ID of y-values array that name applies to
     * @param s   SeriesData from which to pull name
     */
    @Throws(JSONException::class)
    private fun setName(yID: String, s: SeriesData) {
        val name = s.getConfiguration("name", "")
        mNames.put(yID, name)
        mXNames.put(yID, s.getConfiguration("x-name", mData.getConfiguration("x-title", "x")))
    }

    /**
     * Set shape of points to be drawn for series.
     *
     * @param yID ID of y-values that style applies to
     * @param s   SeriesData from which to pull style
     */
    @Throws(JSONException::class)
    private fun setPointStyle(yID: String, s: SeriesData) {
        var symbol: String
        if (mData.getType() == GraphUtil.TYPE_BAR) {
            // point-style doesn't apply to bar charts
            symbol = "none"
        } else if (mData.getType() == GraphUtil.TYPE_BUBBLE) {
            // point-style doesn't apply to bubble charts,
            // but this'll make the legend symbol a circle
            symbol = "circle"
        } else {
            symbol = s.getConfiguration("point-style", "circle").lowercase()
        }
        if (symbol == "triangle") {
            symbol = "triangle-up"
        }
        mPointStyles.put(yID, symbol)
    }

    /**
     * Set series type: line, bar, area, etc.
     *
     * @param yID ID of y-values array corresponding with series
     * @param s   SeriesData determining what the type will be
     */
    @Throws(JSONException::class)
    private fun setType(yID: String, s: SeriesData) {
        val type: String = if (mData.getType() == GraphUtil.TYPE_BUBBLE) {
            "scatter"
        } else if (mData.getType() == GraphUtil.TYPE_BAR) {
            "bar"
        } else if (s.getConfiguration("fill-below") != null) {
            "area"
        } else {
            "line"
        }
        mTypes.put(yID, type)
    }

    /**
     * Set which y axis a series is associated with (primary or secondary).
     *
     * @param yID ID of y-values to associate with the axis
     * @param s   SeriesData to pull y axis from
     */
    @Throws(JSONException::class)
    private fun setYAxis(yID: String, s: SeriesData) {
        val isSecondaryY = java.lang.Boolean.valueOf(s.getConfiguration("secondary-y", "false"))
        mAxes.put(yID, if (isSecondaryY) "y2" else "y")
    }
}
