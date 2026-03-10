package org.commcare.core.graph.suite

import org.commcare.core.graph.model.AnnotationData
import org.commcare.core.graph.model.BubblePointData
import org.commcare.core.graph.model.ConfigurableData
import org.commcare.core.graph.model.GraphData
import org.commcare.core.graph.model.SeriesData
import org.commcare.core.graph.model.XYPointData
import org.commcare.core.graph.util.GraphUtil
import org.commcare.suite.model.DetailTemplate
import org.commcare.suite.model.Text
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Defines a graph: type, set of series, set of text annotations, and key-value-based configuration.
 *
 * @author jschweers
 */
class Graph : Externalizable, DetailTemplate, Configurable {
    private var mType: String? = null
    private var mSeries = ArrayList<XYSeries>()
    private var mConfiguration = HashMap<String, Text>()
    private var mAnnotations = ArrayList<Annotation>()

    constructor() {
        // defaults initialized above
    }

    fun getType(): String? = mType

    fun setType(type: String?) {
        mType = type
    }

    fun addSeries(s: XYSeries) {
        mSeries.add(s)
    }

    fun addAnnotation(a: Annotation) {
        mAnnotations.add(a)
    }

    override fun getConfiguration(key: String): Text? = mConfiguration[key]

    override fun setConfiguration(key: String, value: Text) {
        mConfiguration[key] = value
    }

    override fun getConfigurationKeys(): Iterator<*> = mConfiguration.keys.iterator()

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        mType = ExtUtil.readString(`in`)
        @Suppress("UNCHECKED_CAST")
        mConfiguration = ExtUtil.read(`in`, ExtWrapMap(String::class.java, Text::class.java), pf) as HashMap<String, Text>
        @Suppress("UNCHECKED_CAST")
        mSeries = ExtUtil.read(`in`, ExtWrapListPoly(), pf) as ArrayList<XYSeries>
        @Suppress("UNCHECKED_CAST")
        mAnnotations = ExtUtil.read(`in`, ExtWrapList(Annotation::class.java), pf) as ArrayList<Annotation>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, mType)
        ExtUtil.write(out, ExtWrapMap(mConfiguration))
        ExtUtil.write(out, ExtWrapListPoly(mSeries))
        ExtUtil.write(out, ExtWrapList(mAnnotations))
    }

    override fun evaluate(context: EvaluationContext?): GraphData {
        val data = GraphData()
        data.setType(mType)
        evaluateConfiguration(this, data, context!!)
        evaluateSeries(data, context)
        evaluateAnnotations(data, context)
        return data
    }

    /**
     * Helper for evaluate. Looks at annotations only.
     */
    private fun evaluateAnnotations(graphData: GraphData, context: EvaluationContext) {
        for (a in mAnnotations) {
            graphData.addAnnotation(
                AnnotationData(
                    a.getX()!!.evaluate(context),
                    a.getY()!!.evaluate(context),
                    a.getAnnotation()!!.evaluate(context)
                )
            )
        }
    }

    /**
     * Helper for evaluate. Looks at configuration only.
     */
    private fun evaluateConfiguration(template: Configurable, data: ConfigurableData, context: EvaluationContext) {
        val e = template.getConfigurationKeys()
        val nonvariables = ArrayList<String>()
        val prefix = "var-"
        while (e.hasNext()) {
            val key = e.next() as String
            if (key.startsWith(prefix)) {
                val value = template.getConfiguration(key)!!.evaluate(context)
                context.setVariable(key.substring(prefix.length), value)
            } else {
                nonvariables.add(key)
            }
        }
        for (key in nonvariables) {
            val value = template.getConfiguration(key)!!.evaluate(context)
            data.setConfiguration(key, value)
        }
    }

    /**
     * Helper for evaluate. Looks at all series.
     */
    private fun evaluateSeries(graphData: GraphData, context: EvaluationContext) {
        try {
            for (s in mSeries) {
                val pointConfiguration = HashMap<String, Text>()
                val e = s.getPointConfigurationKeys()
                while (e.hasNext()) {
                    val key = e.next() as String
                    val value = s.getConfiguration(key)
                    if (value != null) {
                        pointConfiguration[key] = value
                    }
                }

                val seriesData = SeriesData()
                val seriesContext = EvaluationContext(context, context.contextRef)

                val refList = expandNodeSet(s, context)
                val expandedConfiguration = HashMap<String, ArrayList<String>>()
                val eKeys = pointConfiguration.keys.iterator()
                while (eKeys.hasNext()) {
                    expandedConfiguration[eKeys.next() as String] = ArrayList()
                }

                for (ref in refList) {
                    val refContext = EvaluationContext(seriesContext, ref)
                    val eKeys2 = pointConfiguration.keys.iterator()
                    while (eKeys2.hasNext()) {
                        val key = eKeys2.next() as String
                        val value = pointConfiguration[key]!!.evaluate(refContext)
                        expandedConfiguration[key]!!.add(value)
                    }
                    val x = s.evaluateX(refContext)
                    val y = s.evaluateY(refContext)
                    if (x != null && y != null) {
                        if (graphData.getType() == GraphUtil.TYPE_BUBBLE) {
                            val radius = (s as BubbleSeries).evaluateRadius(refContext)
                            seriesData.addPoint(BubblePointData(x, y, radius))
                        } else {
                            seriesData.addPoint(XYPointData(x, y))
                        }
                    }
                }
                graphData.addSeries(seriesData)

                val eExpanded = expandedConfiguration.keys.iterator()
                while (eExpanded.hasNext()) {
                    val key = eExpanded.next() as String
                    val json = StringBuffer()
                    for (pointValue in expandedConfiguration[key]!!) {
                        json.append(",'")
                        json.append(pointValue)
                        json.append("'")
                    }
                    if (json.isNotEmpty()) {
                        json.deleteCharAt(0)
                    }
                    json.insert(0, "[")
                    json.append("]")
                    val value = Text.PlainText(json.toString())
                    s.setExpandedConfiguration(key, value)
                }

                // Handle configuration after data, since data processing may update configuration
                evaluateConfiguration(s, seriesData, seriesContext)
                // Guess at names for series, if they weren't provided
                if (seriesData.getConfiguration("name") == null) {
                    seriesData.setConfiguration("name", s.getY()!!)
                }
                if (seriesData.getConfiguration("x-name") == null) {
                    seriesData.setConfiguration("x-name", s.getX()!!)
                }
            }
        } catch (e: XPathSyntaxException) {
            e.printStackTrace()
        }
    }

    companion object {
        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun expandNodeSet(series: XYSeries, context: EvaluationContext): ArrayList<TreeReference> {
            return try {
                // Attempt to evaluate the nodeSet, which will succeed if this is just a path expression
                context.expandReference(XPathReference.getPathExpr(series.getNodeSet()!!).getReference())
            } catch (e: XPathTypeMismatchException) {
                // If that fails, try treating the nodeSet as a more complex expression that itself returns a path
                val xpe: XPathExpression = XPathParseTool.parseXPath(series.getNodeSet()!!)!!
                val nodeSet = xpe.eval(context) as String
                context.expandReference(XPathReference.getPathExpr(nodeSet).getReference())
            } ?: ArrayList()
        }
    }
}
