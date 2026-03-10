package org.javarosa.core.model.data

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author Clayton Sims
 * @date May 19, 2009
 */
class BooleanData : IAnswerData {
    private var data: Boolean = false

    /**
     * NOTE: ONLY FOR SERIALIZATION
     */
    constructor()

    constructor(data: Boolean) {
        this.data = data
    }

    override fun clone(): IAnswerData {
        return BooleanData(data)
    }

    override fun getDisplayText(): String {
        return if (data) "True" else "False"
    }

    override fun getValue(): Any {
        return data
    }

    override fun setValue(o: Any?) {
        data = o as Boolean
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        data = `in`.readBoolean()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        out.writeBoolean(data)
    }

    override fun uncast(): UncastData {
        return UncastData(if (data) "1" else "0")
    }

    override fun cast(data: UncastData): BooleanData {
        if ("1" == data.value) {
            return BooleanData(true)
        }

        if ("0" == data.value) {
            return BooleanData(false)
        }

        throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type Boolean")
    }
}
