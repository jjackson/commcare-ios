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
import kotlin.jvm.JvmField

class XPathVariableReference : XPathExpression {
    @JvmField
    var id: XPathQName? = null

    @Suppress("unused")
    constructor() // for deserialization

    constructor(id: XPathQName) {
        this.id = id
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        return evalContext.getVariable(id.toString())!!
    }

    override fun toString(): String {
        return "{var:${id.toString()}}"
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathVariableReference) {
            return id == o.id
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        id = SerializationHelpers.readExternalizable(`in`, pf) { XPathQName() }
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.write(out, id!!)
        SerializationHelpers.write(out, cacheState)
    }

    override fun toPrettyString(): String {
        return "\$${id.toString()}"
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
    }
}
