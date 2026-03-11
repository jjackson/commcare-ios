package org.javarosa.core.model.instance


import org.javarosa.core.util.ArrayUtilities
import org.javarosa.core.util.Interner
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class TreeReferenceLevel : Externalizable {

    private var name: String? = null
    private var multiplicity: Int = MULT_UNINIT
    private var predicates: ArrayList<XPathExpression>? = null

    constructor() {
        // for externalization
    }

    constructor(name: String?, multiplicity: Int, predicates: ArrayList<XPathExpression>?) {
        this.name = name
        this.multiplicity = multiplicity
        this.predicates = predicates
    }

    constructor(name: String?, multiplicity: Int) : this(name, multiplicity, null)

    fun getMultiplicity(): Int = multiplicity

    fun getName(): String? = name

    fun setMultiplicity(mult: Int): TreeReferenceLevel {
        return TreeReferenceLevel(name, mult, predicates).intern()
    }

    /**
     * Create a copy of this level with updated predicates.
     *
     * @param xpe vector of xpath expressions representing predicates to attach
     *            to a copy of this reference level.
     * @return a (cached-)copy of this reference level with the predicates argument
     * attached.
     */
    fun setPredicates(xpe: ArrayList<XPathExpression>?): TreeReferenceLevel {
        return TreeReferenceLevel(name, multiplicity, xpe).intern()
    }

    fun getPredicates(): ArrayList<XPathExpression>? = predicates

    fun shallowCopy(): TreeReferenceLevel {
        return TreeReferenceLevel(
            name, multiplicity,
            ArrayUtilities.vectorCopy(predicates)
        ).intern()
    }

    fun setName(name: String?): TreeReferenceLevel {
        return TreeReferenceLevel(name, multiplicity, predicates).intern()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val rawName = SerializationHelpers.readString(`in`)
        name = if (rawName.isEmpty()) null else rawName
        multiplicity = SerializationHelpers.readInt(`in`)
        @Suppress("UNCHECKED_CAST")
        val rawPredicates = SerializationHelpers.readListPoly(`in`, pf) as ArrayList<XPathExpression>
        predicates = if (rawPredicates.isEmpty()) null else rawPredicates
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, name ?: "")
        SerializationHelpers.writeNumeric(out, multiplicity.toLong())
        SerializationHelpers.writeListPoly(out, predicates ?: ArrayList<Any>())
    }

    override fun hashCode(): Int {
        var predPart = 0
        if (predicates != null) {
            for (xpe in predicates!!) {
                predPart = predPart xor xpe.hashCode()
            }
        }

        return (name?.hashCode() ?: 0) xor multiplicity xor predPart
    }

    /**
     * Two TreeReferenceLevels are equal if they have the same name,
     * multiplicity, and equal predicates.
     *
     * @param o an object to compare against this TreeReferenceLevel object.
     * @return Is object o a TreeReferenceLevel and has the same fields?
     */
    override fun equals(o: Any?): Boolean {
        if (o !is TreeReferenceLevel) {
            return false
        }

        val l = o
        // multiplicity and names match-up
        if (multiplicity != l.multiplicity ||
            (name == null && l.name != null) ||
            (name != null && name != l.name)
        ) {
            return false
        }

        if (predicates == null && l.predicates == null) {
            return true
        }

        // predicates match-up
        if (predicates == null ||
            l.predicates == null ||
            predicates!!.size != l.predicates!!.size
        ) {
            return false
        }

        // predicate elements are equal
        for (i in 0 until predicates!!.size) {
            if (predicates!![i] != l.predicates!![i]) {
                return false
            }
        }
        return true
    }

    /**
     * Make sure this object has been added to the cache table.
     */
    fun intern(): TreeReferenceLevel {
        return if (!treeRefLevelInterningEnabled || refs == null) {
            this
        } else {
            refs!!.intern(this)
        }
    }

    companion object {
        const val MULT_UNINIT: Int = -16

        // a cache for reference levels, to avoid keeping a bunch of the same levels
        // floating around at run-time.
        private var refs: Interner<TreeReferenceLevel>? = null

        // Do we want to keep a cache of all reference levels?
        val treeRefLevelInterningEnabled: Boolean = true

        /**
         * Used by J2ME
         */
        fun attachCacheTable(cacheTable: Interner<TreeReferenceLevel>?) {
            refs = cacheTable
        }
    }
}
