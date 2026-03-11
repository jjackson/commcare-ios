package org.javarosa.core.model.instance

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzable
import org.javarosa.xpath.analysis.XPathAnalyzer
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

// TODO: This class needs to be immutable so that we can perform caching optimizations.
class TreeReference : Externalizable, XPathAnalyzable {

    private var cachedHashCode = -1

    // -1 = absolute, 0 = context node, 1 = parent, 2 = grandparent ...
    private var refLevel: Int = 0
    @JvmField
    internal var contextType: Int = 0

    /**
     * Name of the reference's root, if it is a non-main instance, otherwise
     * null.
     */
    @JvmField
    internal var instanceName: String? = null

    private var data: ArrayList<TreeReferenceLevel>? = null

    // This value will be computed lazily during calls to size(); every time
    // 'data' changes size, set it to -1 and compute it on demand.
    private var size = -1

    constructor() {
        instanceName = null
        data = ArrayList()
    }

    constructor(instanceName: String?, refLevel: Int) : this(instanceName, refLevel, -1)

    private constructor(instanceName: String?, refLevel: Int, contextType: Int) {
        this.instanceName = instanceName
        this.refLevel = refLevel
        this.contextType = contextType
        this.data = ArrayList()
        setupContextTypeFromInstanceName()
    }

    private fun setupContextTypeFromInstanceName() {
        if (this.instanceName == null) {
            if (this.refLevel == REF_ABSOLUTE) {
                this.contextType = CONTEXT_ABSOLUTE
            } else {
                this.contextType = CONTEXT_INHERITED
            }
        } else {
            this.contextType = CONTEXT_INSTANCE
        }
    }

    fun getInstanceName(): String? = instanceName

    fun getMultiplicity(index: Int): Int = data!![index].getMultiplicity()

    fun getName(index: Int): String? = data!![index].getName()

    fun getMultLast(): Int = data!!.last().getMultiplicity()

    fun getNameLast(): String? = data!!.last().getName()

    fun setMultiplicity(i: Int, mult: Int) {
        cachedHashCode = -1
        data!!.set(i, data!![i].setMultiplicity(mult))
    }

    /**
     * How many reference levels are present? Compute this value on demand and
     * cache it.
     *
     * @return the number of reference levels
     */
    fun size(): Int {
        // csims@dimagi.com - this seems unecessary but is a shocking
        // performance difference due to the number of high-churn circumstances
        // where this call is made.
        if (size == -1) {
            size = data!!.size
        }
        return size
    }

    private fun add(level: TreeReferenceLevel) {
        cachedHashCode = -1
        size = -1
        data!!.add(level)
    }

    fun add(name: String?, mult: Int) {
        add(TreeReferenceLevel(name, mult).intern())
    }

    /**
     * Store a copy of the reference level at level 'key'.
     *
     * @param key reference level at which to attach predicate vector argument.
     * @param xpe vector of xpath expressions representing predicates to attach
     *            to a reference level.
     */
    fun addPredicate(key: Int, xpe: ArrayList<XPathExpression>) {
        cachedHashCode = -1
        data!!.set(key, data!![key].setPredicates(xpe))
    }

    /**
     * Get the predicates for the reference level at level 'key'.
     *
     * @param key reference level at which to grab the predicates.
     * @return the predicates for the specified reference level.
     */
    fun getPredicate(key: Int): ArrayList<XPathExpression>? {
        return data!![key].getPredicates()
    }

    /**
     * @return Do any of the reference levels have predicates attached to them?
     */
    fun hasPredicates(): Boolean {
        for (level in data!!) {
            if (level.getPredicates() != null) {
                return true
            }
        }
        return false
    }

    /**
     * Create a copy of this object without any predicates attached to its
     * reference levels.
     *
     * @return a copy of this tree reference without any predicates
     */
    fun removePredicates(): TreeReference {
        val predicateless = cloneWithEmptyData()
        for (referenceLevel in data!!) {
            predicateless.add(referenceLevel.setPredicates(null))
        }
        return predicateless
    }

    fun getRefLevel(): Int = refLevel

    fun setRefLevel(refLevel: Int) {
        cachedHashCode = -1
        this.refLevel = refLevel
    }

    fun setContextType(contextType: Int) {
        this.contextType = contextType
    }

    fun incrementRefLevel() {
        cachedHashCode = -1
        if (!isAbsolute) {
            refLevel++
        }
    }

    val isAbsolute: Boolean
        get() = refLevel == REF_ABSOLUTE

    /**
     * Return a copy of the reference
     */
    public fun clone(): TreeReference {
        val newRef = cloneWithEmptyData()

        for (l in data!!) {
            newRef.add(l.shallowCopy())
        }

        return newRef
    }

    /**
     * Return a copy of the TreeReference that doesn't include any of the
     * TreeReferenceLevels. Useful when we are just going to overwrite the
     * levels with new data anyways.
     *
     * @return a clone of this object that doesn't include any reference level
     * data.
     */
    private fun cloneWithEmptyData(): TreeReference {
        return TreeReference(instanceName, refLevel, contextType)
    }

    /*
     * Chop the lowest level off the ref so that the ref now represents the
     * parent of the original ref. Return true if we successfully got the
     * parent, false if there were no higher levels
     */
    private fun removeLastLevel(): Boolean {
        val oldSize = size()
        cachedHashCode = -1
        this.size = -1
        return if (oldSize == 0) {
            if (isAbsolute) {
                false
            } else {
                refLevel++
                true
            }
        } else {
            data!!.removeAt(oldSize - 1)
            true
        }
    }

    fun getParentRef(): TreeReference? {
        // TODO: level
        val ref = this.clone()
        return if (ref.removeLastLevel()) {
            ref
        } else {
            null
        }
    }

    /**
     * Join this reference with the base reference argument.
     *
     * @param baseRef an absolute reference or a relative reference with only
     *                '../'s
     * @return a join of this reference with the base reference argument.
     * Returns a clone of this reference if it is absolute, and null if this
     * reference has '../'s but baseRef argument a non-empty relative reference.
     */
    fun parent(baseRef: TreeReference): TreeReference? {
        return if (isAbsolute) {
            this.clone()
        } else {
            val newRef = baseRef.clone()
            if (refLevel > 0) {
                if (!baseRef.isAbsolute && baseRef.size() == 0) {
                    // if parent ref is relative and doesn't have any levels,
                    // aggregate '../' count
                    newRef.refLevel += refLevel
                } else {
                    return null
                }
            }

            // copy reference levels over to parent ref
            for (l in this.data!!) {
                newRef.add(l.shallowCopy())
            }

            newRef
        }
    }

    /**
     * Evaluate this reference in terms of a base absolute reference.
     *
     * For instance, anchoring ../../d/e/f to /a/b/c, results in  /a/d/e/f.
     *
     * NOTE: This function works when baseRef contains INDEX_UNBOUND
     * multiplicites. Conditions depend on this behavior, but it is def
     * slightly icky
     *
     * @param baseRef an absolute reference to be anchored to.
     * @return null if base reference isn't absolute or there are too many
     * '../'.
     */
    fun anchor(baseRef: TreeReference): TreeReference? {
        // TODO: Technically we should possibly be modifying context stuff here
        // instead of in the xpath stuff;

        return if (isAbsolute) {
            this.clone()
        } else if (!baseRef.isAbsolute ||
            refLevel > baseRef.size()
        ) {
            // non-absolute anchor ref or this reference has to many '../' for
            // the anchor ref
            null
        } else {
            val newRef = baseRef.clone()
            // remove a level from anchor ref for each '../'
            for (i in 0 until refLevel) {
                newRef.removeLastLevel()
            }
            // copy level data from this ref to the anchor ref
            for (i in 0 until size()) {
                newRef.add(this.data!![i].shallowCopy())
            }
            newRef
        }
    }

    /**
     * Evaluate this reference in terms of the base reference argument. If this
     * reference can be made more specific by filters or predicates in the
     * context reference, it does so, but never overwrites existing filters or
     * predicates.
     *
     * @param contextRef the absolute reference used as the base while evaluating
     *                   this reference.
     * @return null if context reference is relative, a clone of this reference
     * if it is absolute and doesn't match the context reference argument.
     */
    fun contextualize(contextRef: TreeReference): TreeReference? {
        // TODO: Technically we should possibly be modifying context stuff here
        // instead of in the xpath stuff;

        if (!contextRef.isAbsolute) {
            return null
        }

        // With absolute node we should know what our instance is, so no
        // further contextualizaiton can be applied unless the instances match
        if (this.isAbsolute) {
            if (this.getInstanceName() == null) {
                // If this refers to the main instance, but our context ref doesn't
                if (contextRef.getInstanceName() != null) {
                    return this.clone()
                }
            } else if (this.getInstanceName() != contextRef.getInstanceName()) {
                // Or if this refers to another instance and the context ref
                // doesn't refer to the same instance
                return this.clone()
            }
        }

        val newRef = anchor(contextRef) ?: return null
        newRef.cachedHashCode = -1
        newRef.contextType = contextRef.getContextType()

        // apply multiplicities and fill in wildcards as necessary, based on the
        // context ref
        var i = 0
        while (i < contextRef.size() && i < newRef.size()) {
            // If the contextRef can provide a definition for a wildcard, do so
            if (NAME_WILDCARD == newRef.getName(i) &&
                NAME_WILDCARD != contextRef.getName(i)
            ) {
                newRef.data!!.set(i, newRef.data!![i].setName(contextRef.getName(i)))
            }

            if (contextRef.getName(i) == newRef.getName(i)) {
                // Only copy over multiplicity info if it won't overwrite any
                // existing preds or filters

                // don't copy multiplicity from context when new ref's
                // multiplicity is already bound or when the context's
                // multiplicity is not a position (but rather an attr or
                // template)
                if (newRef.getPredicate(i) == null &&
                    newRef.getMultiplicity(i) == INDEX_UNBOUND &&
                    contextRef.getMultiplicity(i) >= 0
                ) {
                    newRef.setMultiplicity(i, contextRef.getMultiplicity(i))
                }
            } else {
                break
            }
            i++
        }

        return newRef
    }

    fun relativize(parent: TreeReference): TreeReference? {
        return if (parent.isParentOf(this, false)) {
            val relRef = selfRef()
            for (i in parent.size() until this.size()) {
                val index =
                    if (this.getMultiplicity(i) == INDEX_ATTRIBUTE) INDEX_ATTRIBUTE else INDEX_UNBOUND
                relRef.add(this.getName(i), index)
            }
            relRef
        } else {
            null
        }
    }

    /**
     * Turn an un-ambiguous reference into a generic one. This is acheived by
     * setting the multiplicity of every reference level to unbounded.
     *
     * @return a clone of this reference with every reference level's
     * multiplicity set to unbounded.
     */
    fun genericize(): TreeReference {
        return genericizeAfter(0)
    }

    fun genericizeAfter(levelToStartGenericizing: Int): TreeReference {
        val genericRef = clone()
        for (i in levelToStartGenericizing until genericRef.size()) {
            // TODO: It's not super clear whether template refs should get
            // genericized or not
            if (genericRef.getMultiplicity(i) > -1 ||
                genericRef.getMultiplicity(i) == INDEX_TEMPLATE
            ) {
                genericRef.setMultiplicity(i, INDEX_UNBOUND)
            }
        }
        return genericRef
    }

    /**
     * Are these reference's levels subsumed by equivalently named 'child'
     * levels of the same multiplicity?
     *
     * @param child        check if this reference is a child of the current reference
     * @param properParent when set don't return true if 'child' is equal to
     *                     this
     * @return true if 'this' is parent of 'child' or if 'this' equals 'child'
     * (when properParent is false)
     */
    fun isParentOf(child: TreeReference, properParent: Boolean): Boolean {
        if (refLevel != child.refLevel ||
            child.size() < (size() + if (properParent) 1 else 0)
        ) {
            return false
        }

        for (i in 0 until size()) {
            // check that levels names are the same
            if (this.getName(i) != child.getName(i)) {
                return false
            }

            // check that multiplicities are the same; allowing them to differ
            // if on 0-th level, parent mult is the default and child is
            // unbounded.
            val parMult = this.getMultiplicity(i)
            val childMult = child.getMultiplicity(i)
            if (parMult != INDEX_UNBOUND &&
                parMult != childMult &&
                !(i == 0 && parMult == 0 && childMult == INDEX_UNBOUND)
            ) {
                return false
            }
        }

        return true
    }

    /**
     * clone and extend a reference by one level
     */
    fun extendRef(name: String?, mult: Int): TreeReference {
        // TODO: Shouldn't work for this if this is an attribute ref;
        val childRef = this.clone()
        childRef.add(name, mult)
        return childRef
    }

    /**
     * Equality of two TreeReferences comes down to having the same reference
     * level, and equal reference levels entries.
     *
     * @param o an object to compare against this TreeReference object.
     * @return Is object o a TreeReference with equal reference level entries
     * to this object?
     */
    override fun equals(o: Any?): Boolean {
        // csims@dimagi.com - Replaced this function performing itself fully written out
        // rather than allowing the tree reference levels to denote equality. The only edge
        // case was identifying that /data and /data[0] were always the same. I don't think
        // that should matter, but noting in case there are issues in the future.
        if (this === o) {
            return true
        } else if (o is TreeReference) {
            val ref = o
            if (this.refLevel == ref.refLevel && this.size() == ref.size()) {
                // loop through reference segments, comparing their equality
                for (i in 0 until this.size()) {
                    val thisLevel = data!![i]
                    val otherLevel = ref.data!![i]

                    if (thisLevel != otherLevel) {
                        return false
                    }
                }
                return true
            }
        }

        return false
    }

    override fun hashCode(): Int {
        if (cachedHashCode != -1) {
            return cachedHashCode
        }
        var hash = refLevel
        for (i in 0 until size()) {
            var mult = getMultiplicity(i)
            if (i == 0 && mult == INDEX_UNBOUND) {
                mult = 0
            }

            hash = hash xor (getName(i)?.hashCode() ?: 0)
            hash = hash xor mult

            val predicates = this.getPredicate(i)
            if (predicates != null) {
                var `val` = 0
                for (xpe in predicates) {
                    hash = hash xor `val`
                    hash = hash xor xpe.hashCode()
                    ++`val`
                }
            }
        }
        cachedHashCode = hash
        return hash
    }

    override fun toString(): String {
        return toString(true)
    }

    fun toString(includePredicates: Boolean): String {
        val sb = StringBuffer()
        if (instanceName != null) {
            sb.append("instance(").append(instanceName).append(")")
        } else if (contextType == CONTEXT_ORIGINAL) {
            sb.append("current()/")
        }
        if (isAbsolute) {
            sb.append("/")
        } else {
            for (i in 0 until refLevel)
                sb.append("../")
        }
        for (i in 0 until size()) {
            val name = getName(i)
            val mult = getMultiplicity(i)

            if (mult == INDEX_ATTRIBUTE) {
                sb.append("@")
            }
            sb.append(name)

            if (includePredicates) {
                when (mult) {
                    INDEX_UNBOUND -> {
                        val predicates = this.getPredicate(i)
                        if (predicates != null) {
                            for (expr in predicates) {
                                sb.append("[").append(expr.toPrettyString()).append("]")
                            }
                        }
                    }
                    INDEX_TEMPLATE -> sb.append("[@template]")
                    INDEX_REPEAT_JUNCTURE -> sb.append("[@juncture]")
                    else -> {
                        // Don't show a multiplicity selector if we are
                        // selecting the 1st element, since this is the default
                        // and showing brackets might confuse the user.
                        if ((i > 0 || mult != 0) && mult != -4) {
                            sb.append("[").append(mult + 1).append("]")
                        }
                    }
                }
            }

            if (i < size() - 1) {
                sb.append("/")
            }
        }
        return sb.toString()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        refLevel = ExtUtil.readInt(`in`)
        instanceName = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        contextType = ExtUtil.readInt(`in`)
        val size = ExtUtil.readInt(`in`)
        for (i in 0 until size) {
            val level = ExtUtil.read(`in`, TreeReferenceLevel::class.java, pf) as TreeReferenceLevel
            this.add(level.intern())
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, refLevel.toLong())
        ExtUtil.write(out, ExtWrapNullable(instanceName))
        ExtUtil.writeNumeric(out, contextType.toLong())
        ExtUtil.writeNumeric(out, size().toLong())
        for (l in data!!) {
            ExtUtil.write(out, l)
        }
    }

    /**
     * Intersect this tree reference with another, returning a new tree reference
     * which contains all of the common elements, starting with the root element.
     *
     * Note that relative references by their nature can't share steps, so intersecting
     * any (or by any) relative ref will result in the root ref. Additionally, if the
     * two references don't share any steps, the intersection will consist of the root
     * reference.
     *
     * @param b The tree reference to intersect
     * @return The tree reference containing the common basis of this ref and b
     */
    fun intersect(b: TreeReference): TreeReference {
        var b = b
        if (!this.isAbsolute || !b.isAbsolute) {
            return rootRef()
        }
        if (this == b) {
            return this
        }

        val a: TreeReference
        // A should always be bigger if one ref is larger than the other
        if (this.size() < b.size()) {
            a = b.clone()
            b = this.clone()
        } else {
            a = this.clone()
            b = b.clone()
        }

        // Now, trim the refs to the same length.
        val diff = a.size() - b.size()
        for (i in 0 until diff) {
            a.removeLastLevel()
        }

        val aSize = a.size()
        // easy, but requires a lot of re-evaluation.
        for (i in 0..aSize) {
            if (a == b) {
                return a
            } else if (a.size() == 0) {
                return rootRef()
            } else {
                if (!a.removeLastLevel() || !b.removeLastLevel()) {
                    // I don't think it should be possible for us to get here, so flip if we do
                    throw RuntimeException("Dug too deply into TreeReference during intersection")
                }
            }
        }

        // The only way to get here is if a's size is -1
        throw RuntimeException("Impossible state")
    }

    fun getContextType(): Int = this.contextType

    /**
     * Returns the subreference of this reference up to the level specified.
     *
     * For instance, for the reference:
     * (/data/path/to/node).getSubreference(2) => /data/path/to
     *
     * Used to identify the reference context for a predicate at the same level
     *
     * @param level number of segments to include in the truncated
     *              sub-reference.
     * @return A clone of this reference object that includes steps up the
     * specified level.
     * @throws IllegalArgumentException if this object isn't an absolute
     *                                  reference.
     */
    fun getSubReference(level: Int): TreeReference {
        if (!this.isAbsolute) {
            throw IllegalArgumentException("Cannot subreference a non-absolute ref")
        }

        val subRef = cloneWithEmptyData()
        for (i in 0..level) {
            subRef.add(this.data!![i])
        }
        return subRef
    }

    /**
     * Returns a relative reference starting after the level provided.
     *
     * IE: for the reference
     *
     * (/data/one/two/three).getRelativeReferenceAfter(2) => ./two/three
     *
     * One indexed so you can easily run
     *
     * (/data/one/two/three).getRelativeReferenceAfter((/data/one/).size())
     *
     * Will properly index relative and absolute refs, ie:
     *
     * /a/b/c.gRRA(0) -> /a/b/c
     * and
     * ./a/b/c.gRRA(0) -> ./a/b/c
     *
     * Will strip current() references, though, ie:
     *
     * current()/b/c.getRelativeReferenceAfter(0) -> ./b/c
     *
     *
     * @param level number of segments which should be excluded from the new relative reference
     *
     * @return A new reference object which contains a reference which is relative and contains
     * only the steps after the provided reference level.
     */
    fun getRelativeReferenceAfter(level: Int): TreeReference {
        if (level > this.size()) {
            throw IllegalArgumentException(
                "Attempt to retrieve a relative reference " +
                        "larger(" + level + ") than the size of the ref: " + this.toString()
            )
        }

        val relativeStart = if (this.isAbsolute && level == 0)
            rootRef()
        else
            selfRef()

        for (i in level until this.size()) {
            relativeStart.add(this.data!![i])
        }
        return relativeStart
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)

        if (analyzer.shouldIncludePredicates() && this.hasPredicates()) {

            var contextForPredicates: TreeReference = this
            if (this.contextType == CONTEXT_ORIGINAL) {
                val origRef = analyzer.originalContextRef
                if (origRef == null) {
                    throw AnalysisInvalidException.INSTANCE_NO_ORIGINAL_CONTEXT_REF
                }
                contextForPredicates = this.contextualize(origRef)!!
            } else if (!this.isAbsolute) {
                val ctxRef = analyzer.contextRef
                if (ctxRef == null) {
                    throw AnalysisInvalidException.INSTANCE_NO_CONTEXT_REF
                }
                contextForPredicates = this.contextualize(ctxRef)!!
            }

            for (i in 0 until data!!.size) {
                val subLevel = data!![i]
                if (subLevel.getPredicates() != null) {
                    val subContext = contextForPredicates.removePredicates().getSubReference(i)
                    val subAnalyzer = analyzer.spawnSubAnalyzer(subContext)
                    for (expr in subLevel.getPredicates()!!) {
                        expr.applyAndPropagateAnalyzer(subAnalyzer)
                    }
                }
            }
        }
    }

    companion object {
        // Multiplicity demarcates the position of a given element with respect to
        // other elements of the same name.

        // Since users usually want to select the first instance from the nodeset
        // returned from a reference query, let the default multiplicity be
        // selecting the first node.
        const val DEFAULT_MUTLIPLICITY: Int = 0

        // refers to all instances of an element, e.g. /data/b[-1] refers to b[0]
        // and b[1]
        const val INDEX_UNBOUND: Int = -1

        // 'repeats' (sections of a form that can multiply themselves) are
        // populated with a template that never exists in the form (IE: If you
        // serialized the form to XML it wouldn't be there) but provides the xml
        // structure that should be replicated when a 'repeat' is added
        const val INDEX_TEMPLATE: Int = -2

        // multiplicity flag for an attribute
        const val INDEX_ATTRIBUTE: Int = -4

        const val INDEX_REPEAT_JUNCTURE: Int = -10

        // TODO: Roll these into RefLevel? Or more likely, take absolute ref out of refLevel
        const val CONTEXT_ABSOLUTE: Int = 0
        // context is inherited since the path is relative
        const val CONTEXT_INHERITED: Int = 1
        // use the original context instead of current context, used by the
        // current() command.
        const val CONTEXT_ORIGINAL: Int = 2
        const val CONTEXT_INSTANCE: Int = 4

        @JvmField
        val CONTEXT_TYPES: IntArray =
            intArrayOf(CONTEXT_ABSOLUTE, CONTEXT_INHERITED, CONTEXT_ORIGINAL, CONTEXT_INSTANCE)

        const val REF_ABSOLUTE: Int = -1

        const val NAME_WILDCARD: String = "*"

        /**
         * Build a '/' reference
         *
         * @return a reference that represents a root/'/' path
         */
        @JvmStatic
        fun rootRef(): TreeReference {
            return TreeReference(null, REF_ABSOLUTE, CONTEXT_ABSOLUTE)
        }

        /**
         * Build a '.' reference
         *
         * @return a reference that represents a self/'.' path
         */
        @JvmStatic
        fun selfRef(): TreeReference {
            return TreeReference(null, 0, CONTEXT_INHERITED)
        }

        /**
         * Build a 'current()' reference
         *
         * @return a reference that represents a base 'current()' path
         */
        @JvmStatic
        fun baseCurrentRef(): TreeReference {
            val currentRef = TreeReference(null, 0, CONTEXT_ORIGINAL)
            currentRef.contextType = CONTEXT_ORIGINAL
            return currentRef
        }

        @JvmStatic
        fun buildRefFromTreeElement(elem: AbstractTreeElement?): TreeReference {
            var elem = elem
            var ref = selfRef()

            while (elem != null) {
                val step: TreeReference

                if (elem.getName() != null) {
                    step = TreeReference(elem.getInstanceName(), 0, CONTEXT_INHERITED)
                    step.add(elem.getName(), elem.getMult())
                } else {
                    // All TreeElements are part of a consistent tree, so the root should be in the same instance
                    step = TreeReference(elem.getInstanceName(), REF_ABSOLUTE, CONTEXT_ABSOLUTE)
                }

                ref = ref.parent(step)!!
                elem = elem.getParent()
            }
            return ref
        }
    }
}
