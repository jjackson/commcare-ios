package org.javarosa.core.model.data

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A response to a question requesting an Long Numeric Value
 *
 * @author Clayton Sims
 */
class LongData : IAnswerData {
    private var n: Long = 0

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(n: Long) {
        this.n = n
    }

    constructor(n: Long?) {
        setValue(n)
    }

    override fun clone(): IAnswerData {
        return LongData(n)
    }

    override fun getDisplayText(): String {
        return n.toString()
    }

    override fun getValue(): Any {
        return n
    }

    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        n = o as Long
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        n = ExtUtil.readNumeric(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeNumeric(out, n)
    }

    override fun uncast(): UncastData {
        return UncastData(java.lang.Long.valueOf(n).toString())
    }

    override fun cast(data: UncastData): LongData {
        try {
            return LongData(java.lang.Long.parseLong(data.value))
        } catch (nfe: NumberFormatException) {
            throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type Long")
        }
    }
}
