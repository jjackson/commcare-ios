package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class EndpointAction : Externalizable {
    private var endpointId: String? = null
    private var isBackground: Boolean = false

    constructor()

    constructor(endpointId: String?, isBackground: Boolean) {
        this.endpointId = endpointId
        this.isBackground = isBackground
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        endpointId = ExtUtil.readString(`in`)
        isBackground = ExtUtil.readBool(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, endpointId)
        ExtUtil.writeBool(out, isBackground)
    }

    fun getEndpointId(): String? = endpointId

    fun isBackground(): Boolean = isBackground
}
