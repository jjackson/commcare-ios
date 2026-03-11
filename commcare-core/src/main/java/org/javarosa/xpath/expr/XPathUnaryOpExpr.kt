package org.javarosa.xpath.expr

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

abstract class XPathUnaryOpExpr : XPathOpExpr {
    @JvmField
    var a: XPathExpression? = null

    constructor() // for deserialization of children

    constructor(a: XPathExpression) {
        this.a = a
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathUnaryOpExpr) {
            return a == o.a
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return a.hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        a = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeTagged(out, a!!)
        SerializationHelpers.write(out, cacheState)
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
        this.a!!.applyAndPropagateAnalyzer(analyzer)
    }
}
