package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Model class to represent an argument to Endpoint
 */
class EndpointArgument : Externalizable {
    private var id: String? = null
    private var instanceId: String? = null
    private var instanceSrc: String? = null

    // for serialization
    constructor()

    constructor(id: String?, instanceId: String?, instanceSrc: String?) {
        this.id = id
        this.instanceId = instanceId
        this.instanceSrc = instanceSrc
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        id = ExtUtil.readString(`in`)
        instanceId = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        instanceSrc = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, id)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(instanceId))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(instanceSrc))
    }

    fun getId(): String? = id

    fun getInstanceId(): String? = instanceId

    fun getInstanceSrc(): String? = instanceSrc

    /**
     * If the argument should be processed into a external data instance
     *
     * @return true if the argument defines instance attributes, false otherwise
     */
    fun isInstanceArgument(): Boolean = instanceId != null
}
