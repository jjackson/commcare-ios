package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

abstract class XPathBinaryOpExpr : XPathOpExpr {
    var a: XPathExpression? = null
    var b: XPathExpression? = null
    var op: Int = 0

    constructor() // for deserialization of children

    constructor(op: Int, a: XPathExpression, b: XPathExpression) {
        this.a = a
        this.b = b
        this.op = op
    }

    fun toString(op: String?): String {
        return "{binop-expr:$op,${a.toString()},${b.toString()}}"
    }

    abstract override fun equals(o: Any?): Boolean

    protected fun binOpEquals(binaryOpExpr: XPathBinaryOpExpr): Boolean {
        return op == binaryOpExpr.op && a == binaryOpExpr.a && b == binaryOpExpr.b
    }

    override fun hashCode(): Int {
        return op xor a.hashCode() xor b.hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        op = SerializationHelpers.readInt(`in`)
        readExpressions(`in`, pf)
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    protected open fun readExpressions(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        a = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
        b = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, op.toLong())
        writeExpressions(out)
        SerializationHelpers.write(out, cacheState)
    }

    @Throws(PlatformIOException::class)
    protected open fun writeExpressions(out: PlatformDataOutputStream) {
        SerializationHelpers.writeTagged(out, a!!)
        SerializationHelpers.writeTagged(out, b!!)
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: ArrayList<Any>, sentinal: Any?): Any? {
        //Pivot both args
        val aval = a!!.pivot(model, evalContext, pivots, sentinal)
        val bval = b!!.pivot(model, evalContext, pivots, sentinal)

        //If either is the sentinal, we don't have a good way to represent the resulting expression, so fail
        if (aval === sentinal || bval === sentinal) {
            throw UnpivotableExpressionException()
        }

        //If either has added a pivot, this expression can't produce any more pivots, so signal that
        if (aval == null || bval == null) {
            return null
        }

        //Otherwise, return the value
        return this.eval(model, evalContext)
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
        this.a!!.applyAndPropagateAnalyzer(analyzer)
        this.b!!.applyAndPropagateAnalyzer(analyzer)
    }
}
