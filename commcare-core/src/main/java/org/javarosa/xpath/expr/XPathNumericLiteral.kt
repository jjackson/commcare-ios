package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathNumericLiteral : XPathExpression {
    @JvmField
    var d: Double = 0.0

    @Suppress("unused")
    constructor() // for deserialization

    constructor(d: Double) {
        this.d = d
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        return java.lang.Double.valueOf(d)
    }

    override fun toString(): String {
        return "{num:${java.lang.Double.toString(d)}}"
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathNumericLiteral) {
            return if (java.lang.Double.isNaN(d)) java.lang.Double.isNaN(o.d) else d == o.d
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return java.lang.Long.valueOf(java.lang.Double.doubleToLongBits(d)).hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        if (`in`.readByte() == 0x00.toByte()) {
            d = ExtUtil.readNumeric(`in`).toDouble()
        } else {
            d = ExtUtil.readDecimal(`in`)
        }
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        if (d == d.toInt().toDouble()) {
            out.writeByte(0x00)
            ExtUtil.writeNumeric(out, d.toLong())
        } else {
            out.writeByte(0x01)
            ExtUtil.writeDecimal(out, d)
        }
        ExtUtil.write(out, cacheState)
    }

    override fun toPrettyString(): String {
        return java.lang.Double.toString(d)
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
    }
}
