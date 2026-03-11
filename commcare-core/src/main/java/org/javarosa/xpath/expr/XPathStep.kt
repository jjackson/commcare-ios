package org.javarosa.xpath.expr

import org.javarosa.core.util.Interner
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathStep : Externalizable {
    @JvmField
    var axis: Int = 0
    @JvmField
    var test: Int = 0
    @JvmField
    var predicates: Array<XPathExpression> = emptyArray()

    //test-dependent variables
    @JvmField
    var name: XPathQName? = null //TEST_NAME only
    @JvmField
    var namespace: String? = null //TEST_NAMESPACE_WILDCARD only
    @JvmField
    var literal: String? = null //TEST_TYPE_PROCESSING_INSTRUCTION only

    constructor() // for deserialization

    constructor(axis: Int, test: Int) {
        this.axis = axis
        this.test = test
        this.predicates = emptyArray()
    }

    constructor(axis: Int, name: XPathQName) : this(axis, TEST_NAME) {
        this.name = name
    }

    constructor(axis: Int, namespace: String) : this(axis, TEST_NAMESPACE_WILDCARD) {
        this.namespace = namespace
    }

    override fun toString(): String {
        val sb = StringBuffer()

        sb.append("{step:")
        sb.append(axisStr(axis))
        sb.append(",")
        sb.append(testStr())

        if (predicates.isNotEmpty()) {
            sb.append(",{")
            for (i in predicates.indices) {
                sb.append(predicates[i].toString())
                if (i < predicates.size - 1)
                    sb.append(",")
            }
            sb.append("}")
        }
        sb.append("}")

        return sb.toString()
    }

    fun toPrettyString(): String {
        val sb = StringBuffer()
        var axisPrint = axisStr(axis) ?: ""
        var intermediate = "::"
        var testPrint = testStr() ?: ""
        if (axis == AXIS_CHILD) {
            intermediate = ""
            axisPrint = ""
        } else if (axis == AXIS_ATTRIBUTE) {
            intermediate = ""
            axisPrint = "@"
        } else if (this == ABBR_PARENT()) {
            intermediate = ""
            axisPrint = ""
            testPrint = ".."
        } else if (axis == AXIS_DESCENDANT_OR_SELF) {
            intermediate = ""
            axisPrint = ""
            testPrint = ""
        }

        sb.append(axisPrint)
        sb.append(intermediate)
        sb.append(testPrint)

        if (predicates.isNotEmpty()) {
            for (predicate in predicates) {
                sb.append("[")
                sb.append(predicate.toPrettyString())
                sb.append("]")
            }
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other is XPathStep) {
            //shortcuts for faster evaluation
            if (axis != other.axis || test != other.test || predicates.size != other.predicates.size) {
                return false
            }

            when (test) {
                TEST_NAME -> if (name != other.name) {
                    return false
                }
                TEST_NAMESPACE_WILDCARD -> if (namespace != other.namespace) {
                    return false
                }
                TEST_TYPE_PROCESSING_INSTRUCTION -> if (!ExtUtil.equals(literal, other.literal, false)) {
                    return false
                }
            }

            @Suppress("UNCHECKED_CAST")
            return ExtUtil.arrayEquals(predicates as Array<Any?>, other.predicates as Array<Any?>, false)
        } else {
            return false
        }
    }

    /**
     * "matches" follows roughly the same process as equals(), in that it for a step it will
     * check whether two steps share the same properties (multiplicity, test, axis, etc).
     * The only difference is that match() will allow for a named step to match a step who's name
     * is a wildcard.
     *
     * So
     * /path/
     * will "match"
     * /asterisk/
     *
     * even though they are not equal.
     *
     * Matching is reflexive, consistent, and symmetric, but _not_ transitive.
     */
    internal fun matches(o: XPathStep?): Boolean {
        if (o != null) {
            //shortcuts for faster evaluation
            if (axis != o.axis
                || (test != o.test && !((o.test == TEST_NAME && this.test == TEST_NAME_WILDCARD)
                        || (this.test == TEST_NAME && o.test == TEST_NAME_WILDCARD)))
                || predicates.size != o.predicates.size
            ) {
                return false
            }

            when (test) {
                TEST_NAME -> if (o.test != TEST_NAME_WILDCARD && name != o.name) {
                    return false
                }
                TEST_NAMESPACE_WILDCARD -> if (namespace != o.namespace) {
                    return false
                }
                TEST_TYPE_PROCESSING_INSTRUCTION -> if (!ExtUtil.equals(literal, o.literal, false)) {
                    return false
                }
            }

            @Suppress("UNCHECKED_CAST")
            return ExtUtil.arrayEquals(predicates as Array<Any?>, o.predicates as Array<Any?>, false)
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        var code = this.axis xor
                this.test xor
                (this.name?.hashCode() ?: 0) xor
                (this.literal?.hashCode() ?: 0) xor
                (this.namespace?.hashCode() ?: 0)
        for (xpe in predicates) {
            code = code xor xpe.hashCode()
        }
        return code
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        axis = ExtUtil.readInt(`in`)
        test = ExtUtil.readInt(`in`)

        when (test) {
            TEST_NAME -> name = ExtUtil.read(`in`, XPathQName::class.java, pf) as XPathQName
            TEST_NAMESPACE_WILDCARD -> namespace = ExtUtil.readString(`in`)
            TEST_TYPE_PROCESSING_INSTRUCTION -> literal = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        }

        @Suppress("UNCHECKED_CAST")
        val v = ExtUtil.read(`in`, ExtWrapListPoly(), pf) as ArrayList<*>
        predicates = Array(v.size) { i -> v[i] as XPathExpression }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, axis.toLong())
        ExtUtil.writeNumeric(out, test.toLong())

        when (test) {
            TEST_NAME -> ExtUtil.write(out, name!!)
            TEST_NAMESPACE_WILDCARD -> ExtUtil.writeString(out, namespace!!)
            TEST_TYPE_PROCESSING_INSTRUCTION -> ExtUtil.write(out, ExtWrapNullable(literal))
        }

        val v = ArrayList<XPathExpression>()
        for (predicate in predicates) {
            v.add(predicate)
        }
        ExtUtil.write(out, ExtWrapListPoly(v))
    }

    fun intern(): XPathStep {
        return if (!XPathStepInterningEnabled || refs == null) {
            this
        } else {
            refs!!.intern(this)
        }
    }

    fun testStr(): String? {
        return when (test) {
            TEST_NAME -> name.toString()
            TEST_NAME_WILDCARD -> "*"
            TEST_NAMESPACE_WILDCARD -> "$namespace:*"
            TEST_TYPE_NODE -> "node()"
            TEST_TYPE_TEXT -> "text()"
            TEST_TYPE_COMMENT -> "comment()"
            TEST_TYPE_PROCESSING_INSTRUCTION -> "proc-instr(${if (literal == null) "" else "'$literal'"})"
            else -> null
        }
    }

    companion object {
        const val AXIS_CHILD = 0
        const val AXIS_DESCENDANT = 1
        const val AXIS_PARENT = 2
        const val AXIS_ANCESTOR = 3
        const val AXIS_FOLLOWING_SIBLING = 4
        const val AXIS_PRECEDING_SIBLING = 5
        const val AXIS_FOLLOWING = 6
        const val AXIS_PRECEDING = 7
        const val AXIS_ATTRIBUTE = 8
        const val AXIS_NAMESPACE = 9
        const val AXIS_SELF = 10
        const val AXIS_DESCENDANT_OR_SELF = 11
        const val AXIS_ANCESTOR_OR_SELF = 12

        const val TEST_NAME = 0
        const val TEST_NAME_WILDCARD = 1
        const val TEST_NAMESPACE_WILDCARD = 2
        const val TEST_TYPE_NODE = 3
        const val TEST_TYPE_TEXT = 4
        const val TEST_TYPE_COMMENT = 5
        const val TEST_TYPE_PROCESSING_INSTRUCTION = 6

        const val XPathStepInterningEnabled = true

        private var refs: Interner<XPathStep>? = null

        @JvmStatic
        fun ABBR_SELF(): XPathStep {
            return XPathStep(AXIS_SELF, TEST_TYPE_NODE)
        }

        @JvmStatic
        fun ABBR_PARENT(): XPathStep {
            return XPathStep(AXIS_PARENT, TEST_TYPE_NODE)
        }

        @JvmStatic
        fun ABBR_DESCENDANTS(): XPathStep {
            return XPathStep(AXIS_DESCENDANT_OR_SELF, TEST_TYPE_NODE)
        }

        /**
         * Used by J2ME
         */
        @JvmStatic
        fun attachInterner(refs: Interner<XPathStep>) {
            XPathStep.refs = refs
        }

        @JvmStatic
        fun axisStr(axis: Int): String? {
            return when (axis) {
                AXIS_CHILD -> "child"
                AXIS_DESCENDANT -> "descendant"
                AXIS_PARENT -> "parent"
                AXIS_ANCESTOR -> "ancestor"
                AXIS_FOLLOWING_SIBLING -> "following-sibling"
                AXIS_PRECEDING_SIBLING -> "preceding-sibling"
                AXIS_FOLLOWING -> "following"
                AXIS_PRECEDING -> "preceding"
                AXIS_ATTRIBUTE -> "attribute"
                AXIS_NAMESPACE -> "namespace"
                AXIS_SELF -> "self"
                AXIS_DESCENDANT_OR_SELF -> "descendant-or-self"
                AXIS_ANCESTOR_OR_SELF -> "ancestor-or-self"
                else -> null
            }
        }
    }
}
