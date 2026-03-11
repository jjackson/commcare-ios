package org.javarosa.xpath

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.expr.XPathPathExpr

/**
 * Represents a set of XPath nodes returned from a path or other operation which acts on multiple
 * paths.
 *
 * Current encompasses two states.
 *
 * 1) A nodeset which references between 0 and N nodes which are known about (but, for instance,
 * don't match any predicates or are irrelevant). Some operations cannot be evaluated in this state
 * directly. If more than one node is referenced, it is impossible to return a normal evaluation, for
 * instance.
 *
 * 2) A nodeset which wasn't able to reference into any known model (generally a reference which is
 * written in error). In this state, the size of the nodeset can be evaluated, but the acual reference
 * cannot be returned, since it doesn't have any semantic value.
 *
 * (2) may be a deviation from normal XPath. This should be evaluated in the future.
 *
 * @author ctsims
 */
class XPathLazyNodeset(
    private val unExpandedRef: TreeReference,
    instance: DataInstance<*>?,
    ec: EvaluationContext?
) : XPathNodeset(instance, ec) {

    private val evalLock = Any()
    private var evaluated = false

    private fun performEvaluation() {
        synchronized(evalLock) {
            if (evaluated) {
                return
            }
            val nodes = ec!!.expandReference(unExpandedRef)!!

            //to fix conditions based on non-relevant data, filter the nodeset by relevancy
            var i = 0
            while (i < nodes.size) {
                if (!instance!!.resolveReference(nodes[i], ec)!!.isRelevant) {
                    nodes.removeAt(i)
                    i--
                }
                i++
            }
            this.setReferences(nodes)
            evaluated = true
        }
    }

    /**
     * @return The value represented by this xpath. Can only be evaluated when this xpath represents exactly one
     * reference, or when it represents 0 references after a filtering operation (a reference which _could_ have
     * existed, but didn't, rather than a reference which could not represent a real node).
     */
    override fun unpack(): Any? {
        synchronized(evalLock) {
            if (evaluated) {
                return super.unpack()
            }

            //this element is the important one. For Basic nodeset evaluations (referring to one node with no
            //multiplicites) we should be able to do this without doing the expansion

            //first, see if this treeref is usable without expansion
            var safe = true
            for (i in 0 until unExpandedRef.size()) {
                //We can't evaluated any predicates for sure
                if (unExpandedRef.getPredicate(i) != null) {
                    safe = false
                    break
                }
                val mult = unExpandedRef.getMultiplicity(i)
                if (!(mult >= 0 || mult == TreeReference.INDEX_UNBOUND)) {
                    safe = false
                    break
                }
            }
            if (!safe) {
                performEvaluation()
                return super.unpack()
            }

            // TODO: Evaluate error fallbacks, here. I don't know whether this handles the 0 case
            // the same way, although invalid multiplicities should be fine.
            return try {
                //TODO: This doesn't handle templated nodes (repeats which may exist in the future)
                //figure out if we can roll that in easily. For now the catch handles it
                XPathPathExpr.getRefValue(instance!!, ec!!, unExpandedRef)
            } catch (xpe: XPathException) {
                //This isn't really a best effort attempt, so if we can, see if evaluating cleanly works.
                performEvaluation()
                super.unpack()
            }
        }
    }

    override fun toArgList(): Array<Any?> {
        performEvaluation()
        return super.toArgList()
    }

    override fun getReferences(): ArrayList<TreeReference>? {
        performEvaluation()
        return super.getReferences()
    }

    override fun size(): Int {
        performEvaluation()
        return super.size()
    }

    override fun getRefAt(i: Int): TreeReference {
        performEvaluation()
        return super.getRefAt(i)
    }

    override fun getValAt(i: Int): Any? {
        performEvaluation()
        return super.getValAt(i)
    }

    override fun nodeContents(): String {
        performEvaluation()
        return super.nodeContents()
    }

    fun getUnexpandedRefString(): String {
        return unExpandedRef.toString()
    }
}
