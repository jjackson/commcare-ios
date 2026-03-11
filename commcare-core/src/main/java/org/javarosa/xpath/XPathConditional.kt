package org.javarosa.xpath

import org.javarosa.core.log.FatalException
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathBinaryOpExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathFuncExpr
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.expr.XPathUnaryOpExpr
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathConditional : IConditionExpr {

    private var expr: XPathExpression? = null

    @JvmField
    var xpath: String? = null //not serialized!

    private var hasNow: Boolean = false //indicates whether this XpathConditional contains the now() function (used for timestamping)

    @Throws(XPathSyntaxException::class)
    constructor(xpath: String) {
        hasNow = xpath.contains("now()")
        this.expr = XPathParseTool.parseXPath(xpath)
        this.xpath = xpath
    }

    constructor(expr: XPathExpression?) {
        this.expr = expr
    }

    @Suppress("unused")
    constructor()

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext?): Any? {
        try {
            return FunctionUtils.unpack(expr!!.eval(model, evalContext!!))
        } catch (e: XPathUnsupportedException) {
            if (xpath != null) {
                throw XPathUnsupportedException(xpath)
            } else {
                val contextMessage = String.format(
                    "Error calculating expression: \"%s\", being calculated for \"%s\"",
                    expr!!.toPrettyString(), evalContext!!.contextRef
                )
                throw XPathUnsupportedException(contextMessage)
            }
        }
    }

    override fun eval(model: DataInstance<*>?, evalContext: EvaluationContext?): Boolean {
        return FunctionUtils.toBoolean(evalRaw(model, evalContext))
    }

    override fun evalReadable(model: DataInstance<*>?, evalContext: EvaluationContext?): String? {
        return FunctionUtils.toString(evalRaw(model, evalContext))
    }

    override fun evalNodeset(model: DataInstance<*>?, evalContext: EvaluationContext?): ArrayList<TreeReference> {
        if (expr is XPathPathExpr) {
            val evaluated = expr!!.eval(model, evalContext!!) as XPathNodeset
            return evaluated.getReferences()!!
        } else {
            throw FatalException("evalNodeset: must be path expression")
        }
    }

    /**
     * Gather the references that affect the outcome of evaluating this
     * conditional expression.
     *
     * @param originalContextRef context reference pointing to the nodeset
     *                           reference; used for expanding 'current()'
     * @return References of which this conditional expression depends on. Used
     * for retriggering the expression's evaluation if any of these references
     * value or relevancy calculations once.
     */
    override fun getExprsTriggers(originalContextRef: TreeReference?): ArrayList<TreeReference> {
        val triggers = ArrayList<TreeReference>()
        getExprsTriggersAccumulator(expr!!, triggers, null, originalContextRef)
        return triggers
    }

    override fun hashCode(): Int {
        return expr.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is XPathConditional) {
            return expr == other.expr
        }
        return false
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        expr = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        hasNow = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(expr!!))
        ExtUtil.writeBool(out, hasNow)
    }

    override fun toString(): String {
        return "xpath[$expr]"
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext?): ArrayList<Any> {
        return expr!!.pivot(model, evalContext!!)
    }

    companion object {
        /**
         * Recursive helper to getExprsTriggers with an accumulator trigger vector.
         *
         * @param expr               Current expression we are collecting triggers from
         * @param triggers           Accumulates the references that this object's
         *                           expression value depends upon.
         * @param contextRef         Use this updated context; used, for instance,
         *                           when we move into handling predicates
         * @param originalContextRef Context reference pointing to the nodeset
         *                           reference; used for expanding 'current()'
         */
        @JvmStatic
        private fun getExprsTriggersAccumulator(
            expr: XPathExpression,
            triggers: ArrayList<TreeReference>,
            contextRef: TreeReference?,
            originalContextRef: TreeReference?
        ) {
            if (expr is XPathPathExpr) {
                val ref = expr.getReference()
                var contextualized: TreeReference = ref

                if (ref.getContextType() == TreeReference.CONTEXT_ORIGINAL ||
                    (contextRef == null && !ref.isAbsolute)
                ) {
                    // Expr's ref begins with 'current()' or is relative and the
                    // context ref is missing.
                    contextualized = ref.contextualize(originalContextRef!!)!!
                } else if (contextRef != null) {
                    contextualized = ref.contextualize(contextRef)!!
                }

                // find the references this reference depends on inside of predicates
                for (i in 0 until ref.size()) {
                    val predicates = ref.getPredicate(i) ?: continue

                    // contextualizing with ../'s present means we need to
                    // calculate an offset to grab the appropriate predicates
                    val basePredIndex = contextualized.size() - ref.size()

                    val predicateContext = contextualized.getSubReference(basePredIndex + i)

                    for (predicate in predicates) {
                        getExprsTriggersAccumulator(
                            predicate, triggers,
                            predicateContext, originalContextRef
                        )
                    }
                }
                if (!triggers.contains(contextualized)) {
                    triggers.add(contextualized)
                }
            } else if (expr is XPathBinaryOpExpr) {
                getExprsTriggersAccumulator(
                    expr.a!!, triggers,
                    contextRef, originalContextRef
                )
                getExprsTriggersAccumulator(
                    expr.b!!, triggers,
                    contextRef, originalContextRef
                )
            } else if (expr is XPathUnaryOpExpr) {
                getExprsTriggersAccumulator(
                    expr.a!!, triggers,
                    contextRef, originalContextRef
                )
            } else if (expr is XPathFuncExpr) {
                for (i in expr.args.indices) {
                    getExprsTriggersAccumulator(
                        expr.args[i], triggers,
                        contextRef, originalContextRef
                    )
                }
            }
        }
    }
}
