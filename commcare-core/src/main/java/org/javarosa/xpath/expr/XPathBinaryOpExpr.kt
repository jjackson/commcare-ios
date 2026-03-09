package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Vector

abstract class XPathBinaryOpExpr : XPathOpExpr {
    @JvmField
    var a: XPathExpression? = null
    @JvmField
    var b: XPathExpression? = null
    @JvmField
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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        op = ExtUtil.readInt(`in`)
        readExpressions(`in`, pf)
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(IOException::class, DeserializationException::class)
    protected open fun readExpressions(`in`: DataInputStream, pf: PrototypeFactory) {
        a = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        b = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeNumeric(out, op.toLong())
        writeExpressions(out)
        ExtUtil.write(out, cacheState)
    }

    @Throws(IOException::class)
    protected open fun writeExpressions(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(a!!))
        ExtUtil.write(out, ExtWrapTagged(b!!))
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: Vector<Any>, sentinal: Any?): Any? {
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
