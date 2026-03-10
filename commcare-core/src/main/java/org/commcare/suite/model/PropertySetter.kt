package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * This is just a tiny little struct to make it reasonable to maintain
 * the properties until they are installed. Unfortunately, the serialization
 * framework requires it to be public.
 *
 * @author ctsims
 */
class PropertySetter : Externalizable {
    private var _key: String = ""
    private var _value: String = ""
    @JvmField
    internal var force: Boolean = false

    /**
     * Serialization Only!!!
     */
    constructor()

    internal constructor(key: String, value: String, force: Boolean) {
        this._key = key
        this._value = value
        this.force = force
    }

    fun getKey(): String = _key

    fun getValue(): String = _value

    fun isForce(): Boolean = force

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        _key = ExtUtil.readString(`in`)
        _value = ExtUtil.readString(`in`)
        force = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, _key)
        ExtUtil.writeString(out, _value)
        ExtUtil.writeBool(out, force)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PropertySetter) {
            return false
        }

        return this._key == other._key &&
                this._value == other._value &&
                force == other.force
    }

    override fun hashCode(): Int {
        var result = 11
        result = 31 * result + _key.hashCode()
        result = 31 * result + _value.hashCode()
        result = 31 * result + if (force) 0 else 1
        return result
    }
}
