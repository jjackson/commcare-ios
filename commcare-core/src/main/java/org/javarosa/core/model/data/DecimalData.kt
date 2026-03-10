package org.javarosa.core.model.data

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A response to a question requesting an Decimal Value. Adapted from IntegerData
 *
 * @author Brian DeRenzi
 */
class DecimalData : IAnswerData {
    private var d: Double = 0.0

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(d: Double) {
        this.d = d
    }

    constructor(d: Double?) {
        setValue(d)
    }

    override fun clone(): IAnswerData {
        return DecimalData(d)
    }

    override fun getDisplayText(): String {
        return d.toString()
    }

    override fun getValue(): Any {
        return java.lang.Double.valueOf(d)
    }

    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        d = o as Double
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        d = ExtUtil.readDecimal(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeDecimal(out, d)
    }

    override fun uncast(): UncastData {
        return UncastData(getValue().toString())
    }

    override fun cast(data: UncastData): DecimalData {
        try {
            return DecimalData(java.lang.Double.parseDouble(data.value))
        } catch (nfe: NumberFormatException) {
            throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type Decimal")
        }
    }
}
