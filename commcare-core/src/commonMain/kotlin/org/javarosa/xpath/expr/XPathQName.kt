package org.javarosa.xpath.expr

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

/**
 * An XPathQName is string literal that meets the requirements to be an element or attribute
 * name in an XML document
 */
class XPathQName : Externalizable {
    private var namespace: String? = null
    @JvmField
    var name: String? = null
    private var cachedHashCode: Int = 0

    constructor() // for deserialization

    constructor(qname: String?) {
        val sep = if (qname == null) -1 else qname.indexOf(":")
        if (sep == -1) {
            init(null, qname)
        } else {
            init(qname!!.substring(0, sep), qname.substring(sep + 1))
        }
    }

    constructor(namespace: String?, name: String?) {
        init(namespace, name)
    }

    override fun hashCode(): Int {
        return cachedHashCode
    }

    private fun init(namespace: String?, name: String?) {
        if (name == null
            || name.isEmpty()
            || (namespace != null && namespace.isEmpty())
        )
            throw IllegalArgumentException("Invalid QName")

        this.namespace = namespace
        this.name = name
        cacheCode()
    }

    private fun cacheCode() {
        cachedHashCode = name!!.hashCode() xor (if (namespace == null) 0 else namespace.hashCode())
    }

    override fun toString(): String {
        return if (namespace == null) name!! else "$namespace:$name"
    }

    override fun equals(other: Any?): Boolean {
        if (other is XPathQName) {
            if (cachedHashCode != other.hashCode()) {
                return false
            }
            return SerializationHelpers.nullEquals(namespace, other.namespace, false) && name == other.name
        } else {
            return false
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        namespace = SerializationHelpers.readNullableString(`in`, pf)
        name = SerializationHelpers.readString(`in`)
        cacheCode()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNullable(out, namespace)
        SerializationHelpers.writeString(out, name!!)
    }
}
