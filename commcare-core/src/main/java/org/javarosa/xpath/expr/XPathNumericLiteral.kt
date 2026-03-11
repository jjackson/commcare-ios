package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
class XPathNumericLiteral : XPathExpression {
    var d: Double = 0.0

    @Suppress("unused")
    constructor() // for deserialization

    constructor(d: Double) {
        this.d = d
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        return d
    }

    override fun toString(): String {
        return "{num:${d.toString()}}"
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathNumericLiteral) {
            return if (d.isNaN()) o.d.isNaN() else d == o.d
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return d.toBits().hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        if (`in`.readByte() == 0x00.toByte()) {
            d = SerializationHelpers.readNumeric(`in`).toDouble()
        } else {
            d = SerializationHelpers.readDecimal(`in`)
        }
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        if (d == d.toInt().toDouble()) {
            out.writeByte(0x00)
            SerializationHelpers.writeNumeric(out, d.toLong())
        } else {
            out.writeByte(0x01)
            SerializationHelpers.writeDecimal(out, d)
        }
        SerializationHelpers.write(out, cacheState)
    }

    override fun toPrettyString(): String {
        return d.toString()
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
    }
}
