package org.javarosa.core.model

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

/**
 * A Form Index is an immutable index into a specific question definition that
 * will appear in an interaction with a user.
 *
 * An index is represented by different levels into hierarchical groups.
 *
 * Indices can represent both questions and groups.
 *
 * It is absolutely essential that there be no circularity of reference in
 * FormIndex's, IE, no form index's ancestor can be itself.
 *
 * Datatype Productions:
 * FormIndex =  Null | BOF | EOF |
 *              SimpleIndex(nextIndex:FormIndex, localIndex:int) |
 *              IndexWithMult(nextIndex:FormIndex, localIndex:int, instanceIndex:int)
 *
 * @author Clayton Sims
 */
class FormIndex : Externalizable {

    private var beginningOfForm: Boolean = false
    private var endOfForm: Boolean = false

    /**
     * The index of the questiondef in the current context
     */
    private var localIndex: Int = 0

    /**
     * The multiplicity of the current instance of a repeated question or group
     */
    private var instanceIndex: Int = -1

    /**
     * The next level of this index
     */
    var nextLevel: FormIndex? = null

    private var reference: TreeReference? = null

    // needed for serialization
    constructor()

    /**
     * Constructs a simple form index that references a specific element in
     * a list of elements.
     *
     * @param localIndex An integer index into a flat list of elements
     * @param reference  A reference to the instance element identified by this index;
     */
    constructor(localIndex: Int, reference: TreeReference?) {
        this.localIndex = localIndex
        this.reference = reference
    }

    /**
     * Constructs a simple form index that references a specific element in
     * a list of elements.
     *
     * @param localIndex    An integer index into a flat list of elements
     * @param instanceIndex An integer index expressing the multiplicity
     *                      of the current level
     * @param reference     A reference to the instance element identified by this index;
     */
    constructor(localIndex: Int, instanceIndex: Int, reference: TreeReference?) {
        this.localIndex = localIndex
        this.instanceIndex = instanceIndex
        this.reference = reference
    }

    /**
     * Constructs an index which indexes an element, and provides an index
     * into that elements children
     *
     * @param nextLevel  An index into the referenced element's index
     * @param localIndex An index to an element at the current level, a child
     *                   element of which will be referenced by the nextLevel index.
     * @param reference  A reference to the instance element identified by this index;
     */
    constructor(nextLevel: FormIndex?, localIndex: Int, reference: TreeReference?) : this(localIndex, reference) {
        this.nextLevel = nextLevel
    }

    /**
     * Constructs an index which references an element past the level of
     * specificity of the current context, founded by the currentLevel
     * index.
     * (currentLevel, (nextLevel...))
     */
    constructor(nextLevel: FormIndex, currentLevel: FormIndex?) {
        if (currentLevel == null) {
            this.nextLevel = nextLevel.nextLevel
            this.localIndex = nextLevel.localIndex
            this.instanceIndex = nextLevel.instanceIndex
            this.reference = nextLevel.reference
        } else {
            this.nextLevel = nextLevel
            this.localIndex = currentLevel.getLocalIndex()
            this.instanceIndex = currentLevel.getInstanceIndex()
            this.reference = currentLevel.reference
        }
    }

    /**
     * Constructs an index which indexes an element, and provides an index
     * into that elements children, along with the current index of a
     * repeated instance.
     *
     * @param nextLevel     An index into the referenced element's index
     * @param localIndex    An index to an element at the current level, a child
     *                      element of which will be referenced by the nextLevel index.
     * @param instanceIndex How many times the element referenced has been
     *                      repeated.
     * @param reference     A reference to the instance element identified by this index;
     */
    constructor(nextLevel: FormIndex?, localIndex: Int, instanceIndex: Int, reference: TreeReference?) : this(nextLevel, localIndex, reference) {
        this.instanceIndex = instanceIndex
    }

    fun isInForm(): Boolean {
        return !beginningOfForm && !endOfForm
    }

    /**
     * @return The index of the element in the current context
     */
    fun getLocalIndex(): Int {
        return localIndex
    }

    /**
     * @return The multiplicity of the current instance of a repeated question or group
     */
    fun getInstanceIndex(): Int {
        return instanceIndex
    }

    /**
     * Use this method to get the multiplicity of the deepest repeat group in the index.
     * If no level of the current index has a multiplicity, this method will return -1
     *
     * Examples:
     * - If this index is to 0, 1_1, 0, 1_1, 1_2 this method will return 2
     * - If this index is to 0, 1_1, 1_2, 1_3, 0 this method will return 3
     * - If this index is to 0, 1, 2 this method will return -1
     */
    fun getLastRepeatInstanceIndex(): Int {
        val deepestIndexWithMultiplicity = getDeepestLevelWithInstanceIndex()
        return deepestIndexWithMultiplicity?.getInstanceIndex() ?: -1
    }

    /**
     * @return An index into the deepest level of specificity referenced by this index that has
     * an instance index
     */
    private fun getDeepestLevelWithInstanceIndex(): FormIndex? {
        return getDeepestLevelWithInstanceIndex(null)
    }

    private fun getDeepestLevelWithInstanceIndex(deepestSoFar: FormIndex?): FormIndex? {
        var currentDeepest = deepestSoFar
        if (this.getInstanceIndex() != -1) {
            currentDeepest = this
        }
        return if (this.isTerminal()) {
            currentDeepest
        } else {
            nextLevel!!.getDeepestLevelWithInstanceIndex(currentDeepest)
        }
    }

    /**
     * For the fully qualified element, get the multiplicity of the element's reference
     *
     * @return The terminal element (fully qualified)'s instance index
     */
    fun getElementMultiplicity(): Int {
        return getTerminal().instanceIndex
    }

    fun getLocalReference(): TreeReference? {
        return reference
    }

    /**
     * @return The TreeReference of the fully qualified element described by this
     * FormIndex.
     */
    fun getReference(): TreeReference? {
        return getTerminal().reference
    }

    fun getTerminal(): FormIndex {
        var walker = this
        while (walker.nextLevel != null) {
            walker = walker.nextLevel!!
        }
        return walker
    }

    /**
     * Identifies whether this is a terminal index, in other words whether this
     * index references with more specificity than the current context
     */
    fun isTerminal(): Boolean {
        return nextLevel == null
    }

    fun isEndOfFormIndex(): Boolean {
        return endOfForm
    }

    fun isBeginningOfFormIndex(): Boolean {
        return beginningOfForm
    }

    override fun hashCode(): Int {
        var result = 15
        result = 31 * result + (if (beginningOfForm) 0 else 1)
        result = 31 * result + (if (endOfForm) 0 else 1)
        result = 31 * result + localIndex
        result = 31 * result + instanceIndex
        result = 31 * result + (nextLevel?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FormIndex) return false
        return this.compareTo(other) == 0
    }

    fun compareTo(o: Any?): Int {
        if (o !is FormIndex)
            throw IllegalArgumentException("Attempt to compare Object of type ${o!!.javaClass.name} to a FormIndex")

        val a = this
        val b = o

        if (a.beginningOfForm) {
            return if (b.beginningOfForm) 0 else -1
        } else if (a.endOfForm) {
            return if (b.endOfForm) 0 else 1
        } else {
            //a is in form
            if (b.beginningOfForm) {
                return 1
            } else if (b.endOfForm) {
                return -1
            }
        }

        if (a.localIndex != b.localIndex) {
            return if (a.localIndex < b.localIndex) -1 else 1
        } else if (a.instanceIndex != b.instanceIndex) {
            return if (a.instanceIndex < b.instanceIndex) -1 else 1
        } else if ((a.nextLevel == null) != (b.nextLevel == null)) {
            return if (a.nextLevel == null) -1 else 1
        } else if (a.nextLevel != null) {
            return a.nextLevel!!.compareTo(b.nextLevel)
        } else {
            return 0
        }
    }

    /**
     * @return Only the local component of this Form Index.
     */
    fun snip(): FormIndex {
        return FormIndex(localIndex, instanceIndex, reference)
    }

    /**
     * Takes in a form index which is a subset of this index, and returns the
     * total difference between them. This is useful for stepping up the level
     * of index specificty. If the subIndex is not a valid subIndex of this index,
     * null is returned. Since the FormIndex represented by null is always a subset,
     * if null is passed in as a subIndex, the full index is returned
     *
     * For example:
     * Indices
     * a = 1_0,2,1,3
     * b = 1,3
     *
     * a.diff(b) = 1_0,2
     */
    fun diff(subIndex: FormIndex?): FormIndex? {
        if (subIndex == null) {
            return this
        }
        if (!isSubIndex(this, subIndex)) {
            return null
        }
        if (subIndex == this) {
            return null
        }
        return FormIndex(nextLevel!!.diff(subIndex)!!, this.snip())
    }

    override fun toString(): String {
        val ret = StringBuilder()
        var ref: FormIndex? = this
        while (ref != null) {
            ret.append(ref.getLocalIndex())
                .append(if (ref.getInstanceIndex() == -1) "," else "_${ref.getInstanceIndex()},")
            ref = ref.nextLevel
        }

        return ret.toString().substring(0, ret.lastIndexOf(","))
    }

    /**
     * @return the level of this index relative to the top level of the form
     */
    fun getDepth(): Int {
        var depth = 0
        var ref: FormIndex? = this
        while (ref != null) {
            ref = ref.nextLevel
            depth++
        }
        return depth
    }

    /**
     * Used by Touchforms
     */
    @Suppress("unused")
    fun assignRefs(f: FormDef) {
        var cur: FormIndex? = this

        val indexes = ArrayList<Int>()
        val multiplicities = ArrayList<Int>()
        val elements = ArrayList<IFormElement>()
        f.collapseIndex(this, indexes, multiplicities, elements)

        val curMults = ArrayList<Int>()
        val curElems = ArrayList<IFormElement>()

        var i = 0
        while (cur != null) {
            curMults.add(multiplicities[i])
            curElems.add(elements[i])

            cur.reference = f.getChildInstanceRef(curElems, curMults)

            cur = cur.nextLevel
            i++
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        beginningOfForm = ExtUtil.readBool(`in`)
        endOfForm = ExtUtil.readBool(`in`)
        localIndex = ExtUtil.readInt(`in`)
        instanceIndex = ExtUtil.readInt(`in`)
        reference = ExtUtil.read(`in`, ExtWrapNullable(TreeReference::class.java), pf) as TreeReference?
        nextLevel = ExtUtil.read(`in`, ExtWrapNullable(FormIndex::class.java), pf) as FormIndex?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeBool(out, beginningOfForm)
        ExtUtil.writeBool(out, endOfForm)
        ExtUtil.writeNumeric(out, localIndex.toLong())
        ExtUtil.writeNumeric(out, instanceIndex.toLong())
        ExtUtil.write(out, ExtWrapNullable(reference))
        ExtUtil.write(out, ExtWrapNullable(nextLevel))
    }

    companion object {
        @JvmStatic
        fun createBeginningOfFormIndex(): FormIndex {
            val begin = FormIndex(-1, null)
            begin.beginningOfForm = true
            return begin
        }

        @JvmStatic
        fun createEndOfFormIndex(): FormIndex {
            val end = FormIndex(-1, null)
            end.endOfForm = true
            return end
        }

        private fun isSubIndex(parent: FormIndex?, child: FormIndex): Boolean {
            return child == parent ||
                    (parent != null && isSubIndex(parent.nextLevel, child))
        }

        @JvmStatic
        fun isSubElement(parent: FormIndex, child: FormIndex): Boolean {
            var p = parent
            var c = child
            while (!p.isTerminal() && !c.isTerminal()) {
                if (p.getLocalIndex() != c.getLocalIndex()) {
                    return false
                }
                if (p.getInstanceIndex() != c.getInstanceIndex()) {
                    return false
                }
                p = p.nextLevel!!
                c = c.nextLevel!!
            }
            //If we've gotten this far, at least one of the two is terminal
            if (!p.isTerminal() && c.isTerminal()) {
                //can't be the parent if the child is earlier on
                return false
            } else if (p.getLocalIndex() != c.getLocalIndex()) {
                //Either they're at the same level, in which case only
                //identical indices should match, or they should have
                //the same root
                return false
            } else if (p.getInstanceIndex() != -1 && (p.getInstanceIndex() != c.getInstanceIndex())) {
                return false
            }
            //Barring all of these cases, it should be true.
            return true
        }

        /**
         * @return Do all the entries of two FormIndexes match except for the last instance index?
         */
        @JvmStatic
        fun areSiblings(a: FormIndex, b: FormIndex): Boolean {
            if (a.isTerminal() && b.isTerminal() && a.getLocalIndex() == b.getLocalIndex()) {
                return true
            }
            if (!a.isTerminal() && !b.isTerminal()) {
                return a.getLocalIndex() == b.getLocalIndex() &&
                        areSiblings(a.nextLevel!!, b.nextLevel!!)
            }

            return false
        }

        /**
         * @return Do all the local indexes in the 'parent' FormIndex match the
         * corresponding ones in 'child'?
         */
        @JvmStatic
        fun overlappingLocalIndexesMatch(parent: FormIndex, child: FormIndex): Boolean {
            var p = parent
            var c = child
            if (p.getDepth() > c.getDepth()) {
                return false
            }
            while (!p.isTerminal()) {
                if (p.getLocalIndex() != c.getLocalIndex()) {
                    return false
                }
                p = p.nextLevel!!
                c = c.nextLevel!!
            }
            return p.getLocalIndex() == c.getLocalIndex()
        }
    }
}
