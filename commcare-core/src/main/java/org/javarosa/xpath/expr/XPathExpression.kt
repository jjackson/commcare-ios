package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.RequestAbandonedException
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.model.xform.DataModelSerializer
import org.javarosa.xpath.XPathNodeset

import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import org.javarosa.xml.JvmXmlSerializer
import org.javarosa.xml.PlatformXmlSerializer

abstract class XPathExpression : InFormCacheableExpr(), Externalizable {

    open fun eval(evalContext: EvaluationContext): Any {
        return eval(evalContext.getMainInstance(), evalContext)
    }

    /**
     * Evaluate this expression, potentially capturing any additional
     * information about the evaluation.
     *
     * @return The result of this expression evaluated against the provided context
     */
    open fun eval(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        evalContext.openTrace(this)
        if (Thread.interrupted()) {
            throw RequestAbandonedException()
        }

        val value: Any
        val fromCache: Boolean
        if (isCached(evalContext)) {
            value = getCachedValue()!!
            fromCache = true
        } else {
            value = evalRaw(model, evalContext)
            cache(value, evalContext)
            fromCache = false
        }

        evalContext.reportTraceValue(value, fromCache)
        evalContext.closeTrace()

        return value
    }

    /**
     * Perform the raw evaluation of this expression producing an
     * appropriately typed XPath output with no side effects
     *
     * @return The result of this expression evaluated against the provided context
     */
    protected abstract fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any

    @Throws(UnpivotableExpressionException::class)
    fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext): ArrayList<Any> {
        try {
            val pivots = ArrayList<Any>()
            this.pivot(model, evalContext, pivots, evalContext.contextRef)
            return pivots
        } catch (uee: UnpivotableExpressionException) {
            //Rethrow unpivotable (expected)
            throw uee
        } catch (e: Exception) {
            // Pivots aren't critical, if there was a problem getting one, log the exception so we can fix it, and then just report that.
            Logger.exception("Error during expression pivot", e)
            throw UnpivotableExpressionException(e.message)
        }
    }

    /**
     * Pivot this expression, returning values if appropriate, and adding any pivots to the list.
     *
     * @param model       The model to evaluate the current expression against
     * @param evalContext The evaluation context to evaluate against
     * @param pivots      The list of pivot points in the xpath being evaluated. Pivots should be added to this list.
     * @param sentinal    The value which is being pivoted around.
     * @return null - If a pivot was identified in this expression
     * sentinal - If the current expression represents the sentinal being pivoted
     * any other value - The result of the expression if no pivots are detected
     * @throws UnpivotableExpressionException If the expression is too complex to pivot
     */
    @Throws(UnpivotableExpressionException::class)
    open fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: ArrayList<Any>, sentinal: Any?): Any? {
        return eval(model, evalContext)
    }

    /*======= DEBUGGING ========*/
    // should not compile onto phone

    /* print out formatted expression tree */

    private var indent: Int = 0

    private fun printStr(s: String) {
        for (i in 0 until 2 * indent)
            print(" ")
        println(s)
    }

    fun printParseTree() {
        indent = -1
        print(this as Any)
    }

    fun print(o: Any) {
        indent += 1

        if (o is XPathStringLiteral) {
            printStr("strlit {${o.s}}")
        } else if (o is XPathNumericLiteral) {
            printStr("numlit {${o.d}}")
        } else if (o is XPathVariableReference) {
            printStr("var {${o.id}}")
        } else if (o is XPathArithExpr) {
            var op: String? = null
            when (o.op) {
                XPathArithExpr.ADD -> op = "add"
                XPathArithExpr.SUBTRACT -> op = "subtr"
                XPathArithExpr.MULTIPLY -> op = "mult"
                XPathArithExpr.DIVIDE -> op = "div"
                XPathArithExpr.MODULO -> op = "mod"
            }
            printStr("$op {{")
            print(o.a as Any)
            printStr(" } {")
            print(o.b as Any)
            printStr("}}")
        } else if (o is XPathBoolExpr) {
            var op: String? = null
            when (o.op) {
                XPathBoolExpr.AND -> op = "and"
                XPathBoolExpr.OR -> op = "or"
            }
            printStr("$op {{")
            print(o.a as Any)
            printStr(" } {")
            print(o.b as Any)
            printStr("}}")
        } else if (o is XPathCmpExpr) {
            var op: String? = null
            when (o.op) {
                XPathCmpExpr.LT -> op = "lt"
                XPathCmpExpr.LTE -> op = "lte"
                XPathCmpExpr.GT -> op = "gt"
                XPathCmpExpr.GTE -> op = "gte"
            }
            printStr("$op {{")
            print(o.a as Any)
            printStr(" } {")
            print(o.b as Any)
            printStr("}}")
        } else if (o is XPathEqExpr) {
            val op = if (o.op == XPathEqExpr.EQ) "eq" else "neq"
            printStr("$op {{")
            print(o.a as Any)
            printStr(" } {")
            print(o.b as Any)
            printStr("}}")
        } else if (o is XPathUnionExpr) {
            printStr("union {{")
            print(o.a as Any)
            printStr(" } {")
            print(o.b as Any)
            printStr("}}")
        } else if (o is XPathNumNegExpr) {
            printStr("neg {")
            print(o.a as Any)
            printStr("}")
        } else if (o is XPathFuncExpr) {
            if (o.args.isEmpty()) {
                printStr("func {${o.name}, args {none}}")
            } else {
                printStr("func {${o.name}, args {{")
                for (i in o.args.indices) {
                    print(o.args[i] as Any)
                    if (i < o.args.size - 1)
                        printStr(" } {")
                }
                printStr("}}}")
            }
        } else if (o is XPathPathExpr) {
            var init: String? = null

            when (o.initContext) {
                XPathPathExpr.INIT_CONTEXT_ROOT -> init = "root"
                XPathPathExpr.INIT_CONTEXT_RELATIVE -> init = "relative"
                XPathPathExpr.INIT_CONTEXT_EXPR -> init = "expr"
            }

            printStr("path {init-context:$init,")

            if (o.initContext == XPathPathExpr.INIT_CONTEXT_EXPR) {
                printStr(" init-expr:{")
                print(o.filtExpr as Any)
                printStr(" }")
            }

            if (o.steps.isEmpty()) {
                printStr(" steps {none}")
                printStr("}")
            } else {
                printStr(" steps {{")
                for (i in o.steps.indices) {
                    print(o.steps[i] as Any)
                    if (i < o.steps.size - 1)
                        printStr(" } {")
                }
                printStr("}}}")
            }
        } else if (o is XPathFilterExpr) {
            printStr("filter-expr:{{")
            print(o.x as Any)

            if (o.predicates.isEmpty()) {
                printStr(" } predicates {none}}")
            } else {
                printStr(" } predicates {{")
                for (i in o.predicates.indices) {
                    print(o.predicates[i] as Any)
                    if (i < o.predicates.size - 1)
                        printStr(" } {")
                }
                printStr(" }}}")
            }
        } else if (o is XPathStep) {
            val axis = XPathStep.axisStr(o.axis)
            val test = o.testStr()

            if (o.predicates.isEmpty()) {
                printStr("step {axis:$axis test:$test predicates {none}}")
            } else {
                printStr("step {axis:$axis test:$test predicates {{")
                for (i in o.predicates.indices) {
                    print(o.predicates[i] as Any)
                    if (i < o.predicates.size - 1)
                        printStr(" } {")
                }
                printStr("}}}")
            }
        }

        indent -= 1
    }

    // Make sure hashCode and equals are implemented by child classes.
    // If you override one, it is best practice to also override the other.
    abstract override fun hashCode(): Int

    abstract override fun equals(o: Any?): Boolean

    /**
     * @return a best-effort for the cannonical representation
     * of this expression. May not be one-to-one with the original
     * text, and may not be semantically complete, but should ideally
     * provide a human with a clear depiction of the expression.
     */
    abstract fun toPrettyString(): String

    companion object {
        @JvmStatic
        @Throws(PlatformIOException::class)
        fun serializeResult(value: Any?, output: OutputStream) {
            if (value is XPathNodeset && !isLeafNode(value)) {
                serializeElements(value, output)
            } else {
                output.write(FunctionUtils.toString(value).toByteArray(StandardCharsets.UTF_8))
            }
        }

        private fun isLeafNode(value: XPathNodeset): Boolean {
            val refs = value.getReferences()
            if (refs == null || refs.size != 1) {
                return false
            }

            val instance = value.getInstance() ?: return false
            val treeElement = instance.resolveReference(refs[0]) ?: return false
            return treeElement.getNumChildren() == 0
        }

        @Throws(PlatformIOException::class)
        private fun serializeElements(nodeset: XPathNodeset, output: OutputStream) {
            val serializer = JvmXmlSerializer(output, "UTF-8")

            val s = DataModelSerializer(serializer)

            val instance = nodeset.getInstance() ?: return
            val refs = nodeset.getReferences() ?: return

            for (ref in refs) {
                val treeElement = instance.resolveReference(ref) ?: continue
                s.serializeNode(treeElement)
            }
            serializer.flush()
        }
    }
}
