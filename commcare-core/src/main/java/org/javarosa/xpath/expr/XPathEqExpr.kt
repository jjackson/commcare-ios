package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import org.javarosa.core.util.externalizable.PlatformIOException

class XPathEqExpr : XPathBinaryOpExpr {
    private var isEqOp: Boolean = false

    @Suppress("unused")
    constructor() // for deserialization

    constructor(op: Int, a: XPathExpression, b: XPathExpression) : super(op, a, b) {
        isEqOp = (op == EQ)
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val aval = FunctionUtils.unpack(a!!.eval(model, evalContext))
        val bval = FunctionUtils.unpack(b!!.eval(model, evalContext))
        val eq = testEquality(aval, bval)

        return isEqOp == eq
    }

    override fun toString(): String {
        return super.toString(if (isEqOp) "==" else "!=")
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        isEqOp = ExtUtil.readBool(`in`)
        readExpressions(`in`, pf)

        if (isEqOp) {
            op = EQ
        } else {
            op = NEQ
        }
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeBool(out, isEqOp)
        writeExpressions(out)
        ExtUtil.write(out, cacheState)
    }

    override fun toPrettyString(): String {
        val prettyA = a!!.toPrettyString()
        val prettyB = b!!.toPrettyString()

        return if (isEqOp) {
            "$prettyA = $prettyB"
        } else {
            "$prettyA != $prettyB"
        }
    }

    override fun equals(o: Any?): Boolean {
        return (this === o) ||
                ((o is XPathEqExpr) && binOpEquals(o))
    }

    companion object {
        const val EQ = 0
        const val NEQ = 1

        /**
         * Test two XPath Objects for equality the same way that they would be tested
         * if they were the result of an equality operation
         *
         * @param aval XPath Value
         * @param bval XPath Value
         * @return true if the two values are equal, false otherwise
         */
        @JvmStatic
        fun testEquality(aval: Any?, bval: Any?): Boolean {
            var a = aval
            var b = bval
            var eq = false

            if (a is Boolean || b is Boolean) {
                if (a !is Boolean) {
                    a = FunctionUtils.toBoolean(a)
                } else if (b !is Boolean) {
                    b = FunctionUtils.toBoolean(b)
                }

                val ba = a as Boolean
                val bb = b as Boolean
                eq = (ba == bb)
            } else if (a is Double || b is Double) {
                if (a !is Double) {
                    a = FunctionUtils.toNumeric(a)
                } else if (b !is Double) {
                    b = FunctionUtils.toNumeric(b)
                }

                val fa = a as Double
                val fb = b as Double
                eq = kotlin.math.abs(fa - fb) < 1.0e-12
            } else {
                a = FunctionUtils.toString(a)
                b = FunctionUtils.toString(b)
                eq = (a == b)
            }
            return eq
        }
    }
}
