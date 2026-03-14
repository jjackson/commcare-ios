package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import kotlin.jvm.JvmField

/**
 * This is just a tiny little struct to make it reasonable to maintain
 * the properties until they are installed. Unfortunately, the serialization
 * framework requires it to be public.
 *
 * @author ctsims
 */
class PropertySetter : Externalizable {
    var key: String = ""
        private set
    var value: String = ""
        private set
    @JvmField
    var force: Boolean = false

    /**
     * Serialization Only!!!
     */
    constructor()

    internal constructor(key: String, value: String, force: Boolean) {
        this.key = key
        this.value = value
        this.force = force
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        key = SerializationHelpers.readString(`in`)
        value = SerializationHelpers.readString(`in`)
        force = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, key)
        SerializationHelpers.writeString(out, value)
        SerializationHelpers.writeBool(out, force)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PropertySetter) {
            return false
        }

        return this.key == other.key &&
                this.value == other.value &&
                force == other.force
    }

    override fun hashCode(): Int {
        var result = 11
        result = 31 * result + key.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + if (force) 0 else 1
        return result
    }
}
