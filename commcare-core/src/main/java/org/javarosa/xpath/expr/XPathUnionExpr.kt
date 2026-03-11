package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathUnsupportedException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathUnionExpr : XPathBinaryOpExpr {
    @Suppress("unused")
    constructor() // for deserialization

    constructor(a: XPathExpression, b: XPathExpression) : super(-1, a, b)

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        throw XPathUnsupportedException("nodeset union operation")
    }

    override fun toString(): String {
        return super.toString("union")
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        readExpressions(`in`, pf)
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
        op = -1
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        writeExpressions(out)
        ExtUtil.write(out, cacheState)
    }

    override fun toPrettyString(): String {
        return "unsupported union operation"
    }

    override fun equals(o: Any?): Boolean {
        return (this === o) ||
                ((o is XPathUnionExpr) && binOpEquals(o))
    }
}
