package org.javarosa.model.xform
import org.javarosa.core.util.externalizable.JvmExtUtil

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathReference : Externalizable {
    private lateinit var ref: TreeReference
    private var nodeset: String? = null

    /**
     * No-arg constructor for externalization.
     */
    constructor()

    constructor(nodeset: String) {
        ref = getPathExpr(nodeset).getReference()
        this.nodeset = nodeset
    }

    constructor(path: XPathPathExpr) {
        ref = path.getReference()
    }

    constructor(ref: TreeReference) {
        this.ref = ref
    }

    val reference: TreeReference
        get() = ref

    override fun equals(other: Any?): Boolean {
        return other is XPathReference && ref == other.ref
    }

    override fun hashCode(): Int {
        return ref.hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        nodeset = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        ref = JvmExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(nodeset))
        ExtUtil.write(out, ref)
    }

    companion object {
        @JvmStatic
        fun getPathExpr(nodeset: String): XPathPathExpr {
            val path: Any?
            var validNonPathExpr = false

            try {
                path = XPathParseTool.parseXPath(nodeset)
                if (path !is XPathPathExpr) {
                    validNonPathExpr = true
                    throw XPathSyntaxException()
                }
            } catch (xse: XPathSyntaxException) {
                // make these checked exceptions?
                if (validNonPathExpr) {
                    throw XPathTypeMismatchException(
                        "Expected XPath path, got XPath expression: [$nodeset],${xse.message}"
                    )
                } else {
                    xse.printStackTrace()
                    throw XPathException(
                        "Parse error in XPath path: [$nodeset].${if (xse.message == null) "" else "\n${xse.message}"}"
                    )
                }
            }

            return path
        }
    }
}
