package org.javarosa.core.model.data

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Created by Saumya on 6/27/2016.
 */
open class InvalidData(
    private val myErrorMessage: String,
    private val savedValue: IAnswerData
) : IAnswerData {

    override fun setValue(o: Any?) {
    }

    override fun getValue(): Any? {
        return savedValue.getValue()
    }

    override fun getDisplayText(): String? {
        return null
    }

    override fun clone(): IAnswerData {
        return InvalidData(myErrorMessage, savedValue)
    }

    override fun uncast(): UncastData {
        return UncastData(myErrorMessage)
    }

    override fun cast(data: UncastData): IAnswerData? {
        return savedValue
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
    }

    fun getErrorMessage(): String {
        return myErrorMessage
    }
}
