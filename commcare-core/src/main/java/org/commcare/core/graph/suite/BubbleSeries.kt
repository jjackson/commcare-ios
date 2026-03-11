package org.commcare.core.graph.suite

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull

/**
 * Single series ("line") on a bubble chart.
 *
 * @author jschweers
 */
class BubbleSeries : XYSeries {

    private var mRadius: String? = null
    private var mRadiusParse: XPathExpression? = null

    /*
     * Deserialization Only!
     */
    @Suppress("unused")
    constructor()

    constructor(nodeSet: String?) : super(nodeSet)

    fun getRadius(): String? = mRadius

    fun setRadius(radius: String?) {
        mRadius = radius
        mRadiusParse = null
    }

    @Throws(XPathSyntaxException::class)
    override fun parse() {
        super.parse()
        if (mRadiusParse == null) {
            mRadiusParse = parse(mRadius)
        }
    }

    /*
     * Get actual value for radius in a given EvaluationContext.
     */
    @Throws(XPathSyntaxException::class)
    fun evaluateRadius(context: EvaluationContext): String? {
        parse()
        return evaluateExpression(mRadiusParse, context)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        mRadius = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.writeString(out, emptyIfNull(mRadius))
    }
}
