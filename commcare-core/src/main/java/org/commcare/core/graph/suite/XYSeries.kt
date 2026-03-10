package org.commcare.core.graph.suite

import org.commcare.suite.model.Text
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Enumeration
import java.util.Hashtable
import java.util.Vector

/**
 * Single series (line) on an xy graph.
 *
 * @author jschweers
 */
open class XYSeries : Externalizable, Configurable {

    private var mNodeSet: String? = null
    private var mConfiguration: Hashtable<String, Text>? = null

    // List of keys that configure individual points. For these keys, the Text stored in
    // mConfiguration is an XPath expression, which during evaluation will be applied to
    // each point in turn to produce a list of one value for each point. As the "expanded",
    // point-level values are set, keys will be removed from this list.
    private var mPointConfiguration: Vector<String>? = null

    private var mX: String? = null
    private var mY: String? = null

    private var mXParse: XPathExpression? = null
    private var mYParse: XPathExpression? = null

    /*
     * Deserialization only!
     */
    constructor()

    constructor(nodeSet: String?) {
        mNodeSet = nodeSet
        mConfiguration = Hashtable()
        mPointConfiguration = Vector()
        mPointConfiguration!!.addElement("bar-color")
    }

    fun getNodeSet(): String? = mNodeSet

    fun getX(): String? = mX

    fun setX(x: String?) {
        mX = x
        mXParse = null
    }

    fun getY(): String? = mY

    fun setY(y: String?) {
        mY = y
        mYParse = null
    }

    override fun setConfiguration(key: String, value: Text) {
        mConfiguration?.put(key, value)
    }

    fun setExpandedConfiguration(key: String, value: Text) {
        mPointConfiguration?.removeElement(key)
        setConfiguration(key, value)
    }

    fun getPointConfigurationKeys(): Enumeration<*> {
        return mPointConfiguration!!.elements()
    }

    override fun getConfiguration(key: String): Text? {
        return mConfiguration?.get(key)
    }

    override fun getConfigurationKeys(): Enumeration<*> {
        return mConfiguration!!.keys()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        mX = ExtUtil.readString(`in`)
        mY = ExtUtil.readString(`in`)
        mNodeSet = ExtUtil.readString(`in`)
        @Suppress("UNCHECKED_CAST")
        mConfiguration = ExtUtil.read(`in`, ExtWrapMap(String::class.java, Text::class.java), pf) as Hashtable<String, Text>
        @Suppress("UNCHECKED_CAST")
        mPointConfiguration = ExtUtil.read(`in`, ExtWrapList(String::class.java), pf) as Vector<String>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, mX)
        ExtUtil.writeString(out, mY)
        ExtUtil.writeString(out, mNodeSet)
        ExtUtil.write(out, ExtWrapMap(mConfiguration!!))
        ExtUtil.write(out, ExtWrapList(mPointConfiguration!!))
    }

    /*
     * Parse all not-yet-parsed functions in this object.
     */
    @Throws(XPathSyntaxException::class)
    protected open fun parse() {
        if (mXParse == null) {
            mXParse = parse(mX)
        }
        if (mYParse == null) {
            mYParse = parse(mY)
        }
    }

    /*
     * Helper function to parse a single piece of XPath.
     */
    protected fun parse(function: String?): XPathExpression? {
        if (function == null) {
            return null
        }
        return XPathParseTool.parseXPath("string($function)")
    }

    /*
     * Get the actual x value within a given EvaluationContext.
     */
    @Throws(XPathSyntaxException::class)
    fun evaluateX(context: EvaluationContext): String? {
        parse()
        return evaluateExpression(mXParse, context)
    }

    /*
     * Get the actual y value within a given EvaluationContext.
     */
    @Throws(XPathSyntaxException::class)
    fun evaluateY(context: EvaluationContext): String? {
        parse()
        return evaluateExpression(mYParse, context)
    }

    /*
     * Helper for evaluateX and evaluateY.
     */
    protected fun evaluateExpression(expression: XPathExpression?, context: EvaluationContext): String? {
        if (expression != null) {
            val value = expression.eval(context.getMainInstance(), context) as String
            if (value.isNotEmpty()) {
                return value
            }
        }
        return null
    }
}
