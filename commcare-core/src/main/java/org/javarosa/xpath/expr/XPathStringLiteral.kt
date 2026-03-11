package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathStringLiteral : XPathExpression {
    @JvmField
    var s: String = ""

    @Suppress("unused")
    constructor() // for deserialization

    constructor(s: String) {
        this.s = s
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        return s
    }

    override fun toString(): String {
        return "{str:'$s'}" //TODO: s needs to be escaped (' -> \'; \ -> \\)
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathStringLiteral) {
            return s == o.s
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return s.hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        s = ExtUtil.readString(`in`)
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, s)
        ExtUtil.write(out, cacheState)
    }

    override fun toPrettyString(): String {
        return "'$s'"
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
    }
}
