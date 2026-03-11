package org.javarosa.xpath
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmField

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.core.util.formatMessage

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
open class XPathNodeset {

    private var nodes: ArrayList<TreeReference>? = null

    @JvmField
    protected var instance: DataInstance<*>? = null

    @JvmField
    protected var ec: EvaluationContext? = null

    private var pathEvaluated: String? = null
    private var originalPath: String? = null

    private constructor()

    /**
     * for lazy evaluation
     */
    protected constructor(instance: DataInstance<*>?, ec: EvaluationContext?) {
        this.instance = instance
        this.ec = ec
    }

    protected fun setReferences(nodes: ArrayList<TreeReference>?) {
        this.nodes = nodes
    }

    open fun getReferences(): ArrayList<TreeReference>? {
        return this.nodes
    }

    /**
     * @return The value represented by this xpath. Can only be evaluated when this xpath represents exactly one
     * reference, or when it represents 0 references after a filtering operation (a reference which _could_ have
     * existed, but didn't, rather than a reference which could not represent a real node).
     */
    open fun unpack(): Any? {
        if (nodes == null) {
            throw getInvalidNodesetException()
        }

        return if (size() == 0) {
            XPathPathExpr.unpackValue(null)
        } else if (size() > 1) {
            throw XPathTypeMismatchException("XPath nodeset has more than one node [" + nodeContents() + "]; cannot convert multiple nodes to a raw value. Refine path expression to match only one node.")
        } else {
            getValAt(0)
        }
    }

    open fun toArgList(): Array<Any?> {
        if (nodes == null) {
            throw getInvalidNodesetException()
        }

        val args = arrayOfNulls<Any>(size())

        for (i in 0 until size()) {
            val v = getValAt(i)

            //sanity check
            if (v == null) {
                throw RuntimeException("retrived a null value out of a nodeset! shouldn't happen!")
            }

            args[i] = v
        }

        return args
    }

    open fun size(): Int {
        return nodes?.size ?: 0
    }

    open fun getRefAt(i: Int): TreeReference {
        if (nodes == null) {
            throw getInvalidNodesetException()
        }

        return nodes!![i]
    }

    fun getInstance(): DataInstance<*>? {
        return instance
    }

    protected open fun getValAt(i: Int): Any? {
        return XPathPathExpr.getRefValue(instance!!, ec!!, getRefAt(i))
    }

    protected open fun getInvalidNodesetException(): XPathTypeMismatchException {
        if (pathEvaluated != originalPath) {
            if (!originalPath!!.contains("/data")) {
                throw XPathTypeMismatchException(formatMessage(
                    "Logic references {0} which is not a valid question or value." +
                            " You may have forgotten to include the full path to the question " +
                            "(e.g. /data/{0}). (Expanded reference: {1})", originalPath, pathEvaluated))
            } else {
                throw XPathTypeMismatchException(formatMessage(
                    "There was a problem with the path {0}" +
                            " which refers to location which was not found ({1}). " +
                            "This often means you made a typo in the question reference, " +
                            "or the question no longer exists in the form.", originalPath, pathEvaluated))
            }
        } else {
            throw XPathTypeMismatchException(formatMessage(
                "Logic references {0} which is not a valid question or value.", pathEvaluated))
        }
    }

    protected open fun nodeContents(): String {
        return if (nodes == null) {
            "Invalid Path: $pathEvaluated"
        } else {
            printNodeContents(nodes!!)
        }
    }

    companion object {
        @JvmStatic
        fun constructInvalidPathNodeset(pathEvaluated: String?, originalPath: String?): XPathNodeset {
            val nodeset = XPathNodeset()
            nodeset.nodes = null
            nodeset.instance = null
            nodeset.ec = null
            nodeset.pathEvaluated = pathEvaluated
            nodeset.originalPath = originalPath
            return nodeset
        }

        @JvmStatic
        fun printNodeContents(nodes: ArrayList<TreeReference>): String {
            val sb = StringBuffer()
            for (i in 0 until nodes.size) {
                sb.append(nodes[i].toString())
                if (i < nodes.size - 1) {
                    sb.append(";")
                }
            }
            return sb.toString()
        }
    }
}
