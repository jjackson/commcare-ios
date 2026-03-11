package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
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
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        key = SerializationHelpers.readString(`in`)
        display = SerializationHelpers.readExternalizable(`in`, pf) { DisplayUnit() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, key!!)
        SerializationHelpers.write(out, display!!)
    }

    fun getKey(): String? = key

    fun getDisplay(): DisplayUnit? = display
}
