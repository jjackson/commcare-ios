package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers

/**
 * Apps use this model to convey the types of credentials it issues
 */
class Credential : Externalizable {
    var level: String? = null
        private set
    var type: String? = null
        private set

    /**
     * Serialization Only!!!
     */
    constructor()

    constructor(level: String?, type: String?) {
        this.level = level
        this.type = type
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        level = SerializationHelpers.readString(`in`)
        type = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, level!!)
        SerializationHelpers.writeString(out, type!!)
    }

    override fun toString(): String {
        return "Credential{level='$level', type='$type'}"
    }
}
