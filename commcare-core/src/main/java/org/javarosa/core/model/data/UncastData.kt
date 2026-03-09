package org.javarosa.core.model.data

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Uncast data values are those which are not assigned a particular
 * data type. This is relevant when data is read before a datatype is
 * available, or when it must be pulled from external instances.
 *
 * In general, Uncast data should be used when a value is available
 * in string form, and no adequate assumption can be made about the type
 * of data being represented. This is preferable to making the assumption
 * that data is a StringData object, since that will cause issues when
 * select choices or other typed values are expected.
 *
 * @author ctsims
 */
class UncastData : IAnswerData {
    @JvmField
    var value: String? = null

    constructor()

    constructor(value: String?) {
        if (value == null) {
            throw NullPointerException("Attempt to set Uncast Data value to null! IAnswerData objects should never have null values")
        }
        this.value = value
    }

    override fun clone(): UncastData {
        return UncastData(value)
    }

    override fun getDisplayText(): String? {
        return value
    }

    override fun getValue(): Any? {
        return value
    }

    override fun setValue(o: Any?) {
        value = o as String?
    }

    /**
     * @return The string representation of this data. This value should be
     * castable into its appropriate data type.
     */
    fun getString(): String? {
        return value
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        value = ExtUtil.readString(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, value!!)
    }

    override fun uncast(): UncastData {
        return this
    }

    override fun cast(data: UncastData): UncastData {
        return UncastData(data.value)
    }
}
