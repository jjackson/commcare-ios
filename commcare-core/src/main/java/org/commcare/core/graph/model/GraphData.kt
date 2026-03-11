package org.commcare.core.graph.model
import java.util.TreeMap

import org.commcare.core.graph.c3.AxisConfiguration
import org.commcare.core.graph.c3.DataConfiguration
import org.commcare.core.graph.c3.GridConfiguration
import org.commcare.core.graph.c3.LegendConfiguration
import org.commcare.core.graph.util.GraphException
import org.commcare.core.graph.util.GraphUtil
import org.json.JSONException
import org.json.JSONObject
import java.util.SortedMap

/**
 * Contains all of the fully-evaluated data to draw a graph: a type, set of series,
 * set of text annotations, and key-value map of configuration.
 *
 * @author jschweers
 */
class GraphData : ConfigurableData {
    private var mType: String? = null
    private val mSeries = ArrayList<SeriesData>()
    private val mConfiguration = HashMap<String, String>()
    private val mAnnotations = ArrayList<AnnotationData>()

    fun getType(): String? = mType

    fun setType(type: String?) {
        mType = type
    }

    fun getSeries(): ArrayList<SeriesData> = mSeries

    fun addSeries(s: SeriesData) {
        mSeries.add(s)
    }

    fun addAnnotation(a: AnnotationData) {
        mAnnotations.add(a)
    }

    fun getAnnotations(): ArrayList<AnnotationData> = mAnnotations

    override fun setConfiguration(key: String, value: String) {
        mConfiguration[key] = value
    }

    override fun getConfiguration(key: String): String? = mConfiguration[key]

    override fun getConfiguration(key: String, defaultValue: String): String {
        return getConfiguration(key) ?: defaultValue
    }

    /**
     * @return The full HTML page that will comprise this graph (including head, body, and all
     * script and style tags)
     */
    fun getGraphHTML(title: String): String {
        val variables: SortedMap<String, String> = TreeMap()
        val config = JSONObject()
        val html = StringBuilder()
        try {
            // Configure data first, as it may affect the other configurations
            val data = DataConfiguration(this)
            config.put("data", data.getConfiguration())

            val axis = AxisConfiguration(this)
            config.put("axis", axis.getConfiguration())

            val grid = GridConfiguration(this)
            config.put("grid", grid.getConfiguration())

            val legend = LegendConfiguration(this)
            config.put("legend", legend.getConfiguration())

            variables["type"] = "'" + this.getType() + "'"
            variables["config"] = config.toString()

            if (GraphUtil.getLabelCharacterLimit() != -1) {
                variables["characterLimit"] = GraphUtil.getLabelCharacterLimit().toString()
            }

            // For debugging purposes, note that most minified files have un-minified equivalents in the same directory.
            // To use them, switch affix to "max" and get rid of the ignoreAssetsPattern in build.gradle that
            // filters them out of the APK.

            /**
             * We use these tools to update minified equivalents for max files -
             * CSS Minifer - https://www.cleancss.com/css-minify/
             * JS Minifier - https://www.danstools.com/javascript-minify/
             */
            val affix = "min"
            html.append(
                "<html>" +
                        "<head>" +
                        "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/c3.min.css'></link>" +
                        "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/graph." + affix + ".css'></link>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/d3.min.js'></script>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/c3." + affix + ".js' charset='utf-8'></script>" +
                        "<script type='text/javascript'>try {\n"
            )

            html.append(getVariablesHTML(variables, null))
            html.append(getVariablesHTML(data.getVariables(), "data"))
            html.append(getVariablesHTML(axis.getVariables(), "axis"))
            html.append(getVariablesHTML(grid.getVariables(), "grid"))
            html.append(getVariablesHTML(legend.getVariables(), "legend"))

            val titleHTML = "<div id='chart-title'>$title</div>"
            val errorHTML = "<div id='error'></div>"
            val chartHTML = "<div id='chart'></div>"
            html.append(
                "\n} catch (e) { displayError(e); }</script>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/graph." + affix + ".js'></script>" +
                        "</head>" +
                        "<body>" + titleHTML + errorHTML + chartHTML + "</body>" +
                        "</html>"
            )
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GraphException(e.message)
        }

        return html.toString()
    }

    companion object {
        /**
         * Generate HTML to declare given variables in WebView.
         *
         * @param variables OrderedHashTable where keys are variable names and values are JSON
         *                  representations of values.
         * @param namespace Optional. If provided, instead of declaring a separate variable for each
         *                  item in variables, one object will be declared with namespace for a name
         *                  and a property corresponding to each item in variables.
         * @return HTML string
         */
        private fun getVariablesHTML(variables: SortedMap<String, String>, namespace: String?): String {
            val html = StringBuilder()
            if (namespace != null && namespace != "") {
                html.append("var ").append(namespace).append(" = {};\n")
            }
            for (name in variables.keys) {
                if (namespace == null || namespace == "") {
                    html.append("var ").append(name)
                } else {
                    html.append(namespace).append(".").append(name)
                }
                html.append(" = ").append(variables[name]).append(";\n")
            }
            return html.toString()
        }
    }
}
