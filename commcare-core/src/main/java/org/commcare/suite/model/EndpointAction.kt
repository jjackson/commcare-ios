package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull

class EndpointAction : Externalizable {
    private var endpointId: String? = null
    private var isBackground: Boolean = false

    constructor()

    constructor(endpointId: String?, isBackground: Boolean) {
        this.endpointId = endpointId
        this.isBackground = isBackground
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        endpointId = SerializationHelpers.readString(`in`)
        isBackground = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, emptyIfNull(endpointId))
        SerializationHelpers.writeBool(out, isBackground)
    }

    fun getEndpointId(): String? = endpointId

    fun isBackground(): Boolean = isBackground
}
