package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

/**
 * This construct, whose syntax is of the form '(filter-expr pred1 pred2 ...)',
 * is currently unsupported by JavaRosa
 */
class XPathFilterExpr : XPathExpression {
    @JvmField
    var x: XPathExpression? = null
    @JvmField
    var predicates: Array<XPathExpression> = emptyArray()

    constructor() // for deserialization

    constructor(x: XPathExpression, predicates: Array<XPathExpression>) {
        this.x = x
        this.predicates = predicates
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        throw XPathUnsupportedException("filter expression")
    }

    override fun toString(): String {
        val sb = StringBuffer()

        sb.append("{filt-expr:")
        sb.append(x.toString())
        sb.append(",{")
        for (i in predicates.indices) {
            sb.append(predicates[i].toString())
            if (i < predicates.size - 1)
                sb.append(",")
        }
        sb.append("}}")

        return sb.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathFilterExpr) {
            @Suppress("UNCHECKED_CAST")
            return x == o.x && SerializationHelpers.arrayEquals(predicates as Array<Any?>, o.predicates as Array<Any?>, false)
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        var predHash = 0
        for (pred in predicates) {
            predHash = predHash xor pred.hashCode()
        }
        return x.hashCode() xor predHash
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        x = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
        val v = SerializationHelpers.readListPoly(`in`, pf)

        predicates = Array(v.size) { i -> v[i] as XPathExpression }
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        val v = ArrayList<XPathExpression>()
        for (predicate in predicates) {
            v.add(predicate)
        }

        SerializationHelpers.writeTagged(out, x!!)
        SerializationHelpers.writeListPoly(out, v)
        SerializationHelpers.write(out, cacheState)
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: ArrayList<Any>, sentinal: Any?): Any? {
        throw UnpivotableExpressionException()
    }

    override fun toPrettyString(): String {
        return "Unsupported Predicate"
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
        this.x!!.applyAndPropagateAnalyzer(analyzer)
        for (expr in this.predicates) {
            expr.applyAndPropagateAnalyzer(analyzer)
        }
    }
}
