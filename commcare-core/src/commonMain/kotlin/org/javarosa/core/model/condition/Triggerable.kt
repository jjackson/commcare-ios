package org.javarosa.core.model.condition

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField
import kotlin.math.min

/**
 * A triggerable represents an action that should be processed based
 * on a value updating in a model. Triggerables are comprised of two
 * basic components: An expression to be evaluated, and a reference
 * which represents where the resultant value will be stored.
 *
 * A triggerable will dispatch the action it's performing out to
 * all relevant nodes referenced by the context against these current
 * models.
 *
 * @author ctsims
 */
abstract class Triggerable : Externalizable {
    /**
     * The expression which will be evaluated to produce a result
     */
    @JvmField
    var expr: IConditionExpr? = null

    /**
     * References to all of the (non-contextualized) nodes which should be
     * updated by the result of this triggerable
     */
    @JvmField
    var targets: ArrayList<TreeReference> = ArrayList()

    /**
     * Current reference which is the "Basis" of the triggerables being
     * evaluated. This is the highest common root of all of the targets being
     * evaluated.
     */
    @JvmField
    var contextRef: TreeReference? = null  // generic ref used to turn triggers into absolute references

    /**
     * The first context provided to this triggerable before reducing to the common root.
     */
    private var originalContextRef: TreeReference? = null

    private var stopContextualizingAt: Int = -1

    /**
     * Whether this trigger is collecting debug traces
     */
    private var mIsDebugOn: Boolean = false

    /**
     * Debug traces collecting during trigger execution. See the
     * getTriggerTraces method for details.
     */
    private var mTriggerDebugs: HashMap<TreeReference, EvaluationTrace>? = null

    constructor()

    constructor(expr: IConditionExpr?, contextRef: TreeReference?) {
        this.expr = expr
        this.contextRef = contextRef
        this.originalContextRef = contextRef
        this.targets = ArrayList()
        this.stopContextualizingAt = -1
    }

    protected abstract fun eval(instance: FormInstance?, ec: EvaluationContext?): Any?

    protected abstract fun apply(ref: TreeReference?, result: Any?, instance: FormInstance?, f: FormDef?)

    abstract fun canCascade(): Boolean

    /**
     * @return A key string describing the triggerable type used to aggregate and
     * request specific debugging results.
     */
    abstract fun getDebugLabel(): String

    /**
     * @param mDebugMode Whether this triggerable should be collecting trace information
     *                   during execution.
     */
    fun setDebug(mDebugMode: Boolean) {
        this.mIsDebugOn = mDebugMode
        if (mIsDebugOn) {
            mTriggerDebugs = HashMap()
        } else {
            mTriggerDebugs = null
        }
    }

    /**
     * Retrieves evaluation traces collected during execution of this
     * triggerable in debug mode.
     *
     * @return A mapping from tree references impacted by this triggerable, to
     * the root of the evaluation trace that was triggered.
     * @throws IllegalStateException If debugging has not been enabled.
     */
    @Throws(IllegalStateException::class)
    fun getEvaluationTraces(): HashMap<TreeReference, EvaluationTrace> {
        if (!mIsDebugOn) {
            throw IllegalStateException("Evaluation traces requested from triggerable not in debug mode.")
        }
        val debugs = mTriggerDebugs
        return debugs ?: HashMap()
    }

    /**
     * Not for re-implementation, dispatches all of the evaluation
     */
    fun apply(instance: FormInstance?, parentContext: EvaluationContext?,
              context: TreeReference?, f: FormDef?) {
        // The triggering root is the highest level of actual data we can
        // inquire about, but it _isn't_ necessarily the basis for the actual
        // expressions, so we need genericize that ref against the current
        // context
        val ungenericised = originalContextRef!!.contextualize(context!!)!!
        val ec = EvaluationContext(parentContext, ungenericised)
        var triggerEval = ec
        if (mIsDebugOn) {
            triggerEval = EvaluationContext(ec, ec.contextRef)
            triggerEval.setDebugModeOn()
        }

        val result = eval(instance, triggerEval)

        for (baseTargetRef in targets) {
            val targetRef = baseTargetRef.contextualize(ec.contextRef!!)
            val expandedReferences = ec.expandReference(targetRef!!) ?: continue

            for (affectedRef in expandedReferences) {
                if (mIsDebugOn) {
                    mTriggerDebugs!![affectedRef] = triggerEval.getEvaluationTrace()!!
                }
                apply(affectedRef, result, instance, f)
            }
        }
    }

    fun addTarget(target: TreeReference) {
        if (targets.indexOf(target) == -1) {
            targets.add(target)
        }
    }

    fun getTargets(): ArrayList<TreeReference> {
        return targets
    }

    /**
     * This should return true if this triggerable's targets will implicitly modify the
     * value of their children. IE: if this triggerable makes a node relevant/irrelevant,
     * expressions which care about the value of this node's children should be triggered.
     *
     * @return True if this condition should trigger expressions whose targets include
     * nodes which are the children of this node's targets.
     */
    open fun isCascadingToChildren(): Boolean {
        return false
    }

    fun getTriggers(): ArrayList<TreeReference> {
        // grab the relative trigger references from expression
        val relTriggers = expr!!.getExprsTriggers(originalContextRef)

        // construct absolute references by anchoring against the original context reference
        val absTriggers = ArrayList<TreeReference>()
        for (i in 0 until relTriggers.size) {
            val ref = relTriggers[i].anchor(originalContextRef!!)!!
            absTriggers.add(ref)
        }
        return absTriggers
    }

    override fun equals(other: Any?): Boolean {
        if (other is Triggerable) {
            if (this === other) {
                return true
            }

            if (expr == other.expr) {
                // check triggers
                val aTriggers = this.getTriggers()
                val bTriggers = other.getTriggers()

                // order doesn't matter, but triggers in A must be in B and vice versa
                return (subsetOfAndAbsolute(aTriggers, bTriggers) &&
                        subsetOfAndAbsolute(bTriggers, aTriggers))
            }
        }

        return false
    }

    /**
     * @param potentialSubset Ensure set elements are absolute and in the master set
     * @return True if all elements in the potential set are absolute and in
     * the master set.
     */
    private fun subsetOfAndAbsolute(potentialSubset: ArrayList<TreeReference>,
                                     masterSet: ArrayList<TreeReference>): Boolean {
        for (ref in potentialSubset) {
            // csims@dimagi.com - 2012-04-17
            // Added last condition here. We can't actually say whether two triggers
            // are the same purely based on equality if they are relative.
            if (!ref.isAbsolute || masterSet.indexOf(ref) == -1) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var hash = expr?.hashCode() ?: 0
        for (trigRef in getTriggers()) {
            hash = hash xor trigRef.hashCode()
        }
        return hash
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(input: PlatformDataInputStream, pf: PrototypeFactory) {
        expr = SerializationHelpers.readTagged(input, pf) as IConditionExpr
        contextRef = SerializationHelpers.readExternalizable(input, pf) { TreeReference() }
        originalContextRef = SerializationHelpers.readExternalizable(input, pf) { TreeReference() }
        targets = SerializationHelpers.readList(input, pf) { TreeReference() }
        stopContextualizingAt = SerializationHelpers.readInt(input)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeTagged(out, expr!!)
        SerializationHelpers.write(out, contextRef!!)
        SerializationHelpers.write(out, originalContextRef!!)
        SerializationHelpers.writeList(out, targets)
        SerializationHelpers.writeNumeric(out, stopContextualizingAt.toLong())
    }

    override fun toString(): String {
        val sb = StringBuilder("(")
        for (i in 0 until targets.size) {
            sb.append(targets[i].toString())
            if (i < targets.size - 1)
                sb.append(") (")
        }
        sb.append(")")
        return "(trig ($expr) -> ($sb))"
    }

    /**
     * Copy over predicate/multiplicity context from anchorRef into the context
     * ref of this triggerable.
     *
     * If references in the triggerable's expression have predicates that
     * overlap with the context ref, wipe out any contextualization that
     * occurred from anchorRef. Contextual widening is needed to target the
     * correct nodes during the triggerable evaluation: the expression's
     * references might point to a non-existent node, so we want to re-fire
     * evaluation when that node comes into existence
     */
    fun narrowContextBy(anchorRef: TreeReference): TreeReference {
        val contextualizedUsingAnchor = contextRef!!.contextualize(anchorRef)!!
        return if (stopContextualizingAt != -1) {
            contextualizedUsingAnchor.genericizeAfter(stopContextualizingAt)
        } else {
            contextualizedUsingAnchor
        }
    }

    /**
     * Calculate lowest occurring predicate in refInExpr that overlaps with
     * this triggerables context reference.  Needed to narrow the context the
     * correct amount during triggerable evalution.
     *
     * @return copy of refInExpr with predicates cleared.
     */
    fun widenContextToAndClearPredicates(refInExpr: TreeReference): TreeReference {
        val smallestIntersectionForRef = smallestIntersectingLevelWithPred(refInExpr)

        if (smallestIntersectionForRef != -1) {
            if (stopContextualizingAt == -1) {
                stopContextualizingAt = smallestIntersectionForRef
            } else {
                stopContextualizingAt = min(stopContextualizingAt, smallestIntersectionForRef)
            }
        }
        return refInExpr.removePredicates()
    }

    /**
     * Propagate context widening parameters from a triggerable that dominates
     * (causes the firing of) another triggerable.  If dominator's context is
     * widened at a specific point and the dominated context shares part of the
     * widened context, then we must propagate that widening parameter such
     * that the dominated nodes get fired correctly in triggerable evaluation.
     */
    fun updateStopContextualizingAtFromDominator(dominator: Triggerable) {
        if (dominator.stopContextualizingAt != -1 &&
            (stopContextualizingAt == -1 || dominator.stopContextualizingAt < stopContextualizingAt) &&
            dominator.contextRef!!.intersect(contextRef!!).size() >= dominator.stopContextualizingAt) {
            stopContextualizingAt = dominator.stopContextualizingAt
        }
    }

    /**
     * Find position of the first step with a predicate in the provided
     * reference that intersects with this triggerable's context reference
     */
    private fun smallestIntersectingLevelWithPred(refInExpr: TreeReference): Int {
        val intersectionRef = contextRef!!.intersect(refInExpr.removePredicates())
        for (refLevel in 0 until min(refInExpr.size(), intersectionRef.size())) {
            val predicates = refInExpr.getPredicate(refLevel)
            if (predicates != null && predicates.size > 0) {
                return refLevel
            }
        }
        return -1
    }
}
