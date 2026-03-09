package org.javarosa.core.model.data

import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A response to a question requesting an Integer Value
 *
 * @author Clayton Sims
 */
class IntegerData : IAnswerData {
    private var n: Int = 0

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(n: Int) {
        this.n = n
    }

    constructor(n: Int?) {
        setValue(n)
    }

    override fun clone(): IAnswerData {
        return IntegerData(n)
    }

    override fun getDisplayText(): String {
        return n.toString()
    }

    override fun getValue(): Any {
        return DataUtil.integer(n)
    }

    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        n = o as Int
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        n = ExtUtil.readInt(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeNumeric(out, n.toLong())
    }

    override fun uncast(): UncastData {
        return UncastData(DataUtil.integer(n).toString())
    }

    override fun cast(data: UncastData): IntegerData {
        try {
            return IntegerData(Integer.parseInt(data.value))
        } catch (nfe: NumberFormatException) {
            throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type Integer")
        }
    }
}
