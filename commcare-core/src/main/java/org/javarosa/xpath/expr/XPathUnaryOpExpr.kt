package org.javarosa.xpath.expr

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        a = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(a!!))
        ExtUtil.write(out, cacheState)
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
