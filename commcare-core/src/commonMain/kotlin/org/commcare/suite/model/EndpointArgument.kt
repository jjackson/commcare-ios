package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.core.util.externalizable.emptyIfNull

/**
 * Model class to represent an argument to Endpoint
 */
class EndpointArgument : Externalizable {
    var id: String? = null
        private set
    var instanceId: String? = null
        private set
    var instanceSrc: String? = null
        private set

    // for serialization
    constructor()

    constructor(id: String?, instanceId: String?, instanceSrc: String?) {
        this.id = id
        this.instanceId = instanceId
        this.instanceSrc = instanceSrc
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        id = SerializationHelpers.readString(`in`)
        instanceId = nullIfEmpty(SerializationHelpers.readString(`in`))
        instanceSrc = nullIfEmpty(SerializationHelpers.readString(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, emptyIfNull(id))
        SerializationHelpers.writeString(out, emptyIfNull(instanceId))
        SerializationHelpers.writeString(out, emptyIfNull(instanceSrc))
    }

    /**
     * If the argument should be processed into a external data instance
     *
     * @return true if the argument defines instance attributes, false otherwise
     */
    fun isInstanceArgument(): Boolean = instanceId != null
}
