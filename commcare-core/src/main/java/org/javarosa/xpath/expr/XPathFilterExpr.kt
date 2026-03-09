package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Vector

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
            return x == o.x && ExtUtil.arrayEquals(predicates as Array<Any?>, o.predicates as Array<Any?>, false)
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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        x = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        val v = ExtUtil.read(`in`, ExtWrapListPoly(), pf) as Vector<*>

        predicates = Array(v.size) { i -> v.elementAt(i) as XPathExpression }
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        val v = Vector<XPathExpression>()
        for (predicate in predicates) {
            v.addElement(predicate)
        }

        ExtUtil.write(out, ExtWrapTagged(x!!))
        ExtUtil.write(out, ExtWrapListPoly(v))
        ExtUtil.write(out, cacheState)
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: Vector<Any>, sentinal: Any?): Any? {
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
