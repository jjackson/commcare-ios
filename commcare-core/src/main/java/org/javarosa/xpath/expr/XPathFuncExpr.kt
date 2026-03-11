package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

/**
 * Base class for xpath function expressions.
 * Dispatches to runtime function overrides when they exist.
 */
abstract class XPathFuncExpr : XPathExpression {
    @JvmField
    var name: String = ""
    @JvmField
    var args: Array<XPathExpression> = emptyArray()
    @JvmField
    var expectedArgCount: Int = 0
    private var evaluateArgsFirst: Boolean = false

    @Suppress("unused")
    constructor() {
        // for deserialization
    }

    @Throws(XPathSyntaxException::class)
    constructor(name: String, args: Array<XPathExpression>,
                expectedArgCount: Int, evaluateArgsFirst: Boolean) {
        this.name = name
        this.args = args
        this.expectedArgCount = expectedArgCount
        this.evaluateArgsFirst = evaluateArgsFirst

        validateArgCount()
    }

    final override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val evaluatedArgs = evaluateArguments(model, evalContext)

        val handler = evalContext.getFunctionHandlers()[name]
        return if (handler != null) {
            XPathCustomRuntimeFunc.evalCustomFunction(handler, evaluatedArgs, evalContext)
        } else {
            evalBody(model, evalContext, evaluatedArgs)
        }
    }

    private fun evaluateArguments(model: DataInstance<*>?, evalContext: EvaluationContext): Array<Any?> {
        val evaluatedArgs = arrayOfNulls<Any>(args.size)
        if (evaluateArgsFirst) {
            for (i in args.indices) {
                evaluatedArgs[i] = args[i].eval(model, evalContext)
            }
        }
        return evaluatedArgs
    }

    protected abstract fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any

    override fun toString(): String {
        val sb = StringBuffer()

        sb.append("{func-expr:")
        sb.append(name)
        sb.append(",{")
        for (i in args.indices) {
            sb.append(args[i].toString())
            if (i < args.size - 1)
                sb.append(",")
        }
        sb.append("}}")

        return sb.toString()
    }

    override fun toPrettyString(): String {
        val sb = StringBuffer()
        sb.append(name).append("(")
        for (i in args.indices) {
            sb.append(args[i].toPrettyString())
            if (i < args.size - 1) {
                sb.append(", ")
            }
        }
        sb.append(")")
        return sb.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathFuncExpr) {
            //Shortcuts for very easily comprable values
            //We also only return "True" for methods we expect to return the same thing. This is not good
            //practice in Java, since o.equals(o) will return false. We should evaluate that differently.
            //Dec 8, 2011 - Added "uuid", since we should never assume one uuid equals another
            //May 6, 2013 - Added "random", since two calls asking for a random
            if (name != o.name || args.size != o.args.size || name == "uuid" || name == "random") {
                return false
            }

            @Suppress("UNCHECKED_CAST")
            return SerializationHelpers.arrayEquals(args as Array<Any?>, o.args as Array<Any?>, false)
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        var argsHash = 0
        for (arg in args) {
            argsHash = argsHash xor arg.hashCode()
        }
        return name.hashCode() xor argsHash
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        expectedArgCount = SerializationHelpers.readInt(`in`)
        evaluateArgsFirst = SerializationHelpers.readBool(`in`)

        val v = SerializationHelpers.readListPoly(`in`, pf)
        args = Array(v.size) { i -> v[i] as XPathExpression }
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.write(out, expectedArgCount)
        SerializationHelpers.write(out, evaluateArgsFirst)

        val v = ArrayList<XPathExpression>()
        for (arg in args) {
            v.add(arg)
        }
        SerializationHelpers.writeListPoly(out, v)
        SerializationHelpers.write(out, cacheState)
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: ArrayList<Any>, sentinal: Any?): Any? {
        //for now we'll assume that all that functions do is return the composition of their components
        val argVals = arrayOfNulls<Any>(args.size)

        //Identify whether this function is an identity: IE: can reflect back the pivot sentinal with no modification
        val identities = arrayOf("string-length")
        var id = false
        for (identity in identities) {
            if (identity == name) {
                id = true
            }
        }

        //get each argument's pivot
        for (i in args.indices) {
            argVals[i] = args[i].pivot(model, evalContext, pivots, sentinal)
        }

        var pivoted = false
        //evaluate the pivots
        for (argVal in argVals) {
            if (argVal == null) {
                //one of our arguments contained pivots,
                pivoted = true
            } else if (sentinal == argVal) {
                //one of our arguments is the sentinal, return the sentinal if possible
                if (id) {
                    return sentinal
                } else {
                    //This function modifies the sentinal in a way that makes it impossible to capture
                    //the pivot.
                    throw UnpivotableExpressionException()
                }
            }
        }

        if (pivoted) {
            if (id) {
                return null
            } else {
                //This function modifies the sentinal in a way that makes it impossible to capture
                //the pivot.
                throw UnpivotableExpressionException()
            }
        }

        //TODO: Inner eval here with eval'd args to improve speed
        return eval(model, evalContext)
    }

    @Throws(XPathSyntaxException::class)
    protected open fun validateArgCount() {
        if (expectedArgCount != args.size) {
            throw XPathArityException(name, expectedArgCount, args.size)
        }
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
        for (expr in this.args) {
            expr.applyAndPropagateAnalyzer(analyzer)
        }
    }
}
