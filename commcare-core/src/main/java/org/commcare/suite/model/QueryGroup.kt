package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

// Model for <group> node
class QueryGroup : Externalizable {
    private var key: String? = null
    private var display: DisplayUnit? = null

    constructor()

    constructor(key: String?, display: DisplayUnit?) {
        this.key = key
        this.display = display
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        key = ExtUtil.read(`in`, String::class.java, pf) as String
        display = ExtUtil.read(`in`, DisplayUnit::class.java, pf) as DisplayUnit
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, key)
        ExtUtil.write(out, display)
    }

    fun getKey(): String? = key

    fun getDisplay(): DisplayUnit? = display
}
