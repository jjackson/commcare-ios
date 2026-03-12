package org.javarosa.core.services.transport.payload

import org.javarosa.core.util.MultiInputStream
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import kotlin.jvm.JvmField

/**
 * @author Clayton Sims
 * @date Dec 18, 2008
 */
class MultiMessagePayload : IDataPayload {
    /** IDataPayload  */
    @JvmField
    var payloads: ArrayList<Any?> = ArrayList()

    /**
     * Note: Only useful for serialization.
     */
    constructor() {
        //ONLY FOR SERIALIZATION
    }

    /**
     * Adds a payload that should be sent as part of this
     * payload.
     *
     * @param payload A payload that will be transmitted
     *                after all previously added payloads.
     */
    fun addPayload(payload: IDataPayload) {
        payloads.add(payload)
    }

    /**
     * @return A vector object containing each IDataPayload in this payload.
     */
    fun getPayloads(): ArrayList<Any?> {
        return payloads
    }

    @Throws(PlatformIOException::class)
    override fun getPayloadStream(): PlatformInputStream {
        val bigStream = MultiInputStream()
        val en = payloads.iterator()
        while (en.hasNext()) {
            val payload = en.next() as IDataPayload
            bigStream.addStream(payload.getPayloadStream())
        }
        bigStream.prepare()
        return bigStream
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        payloads = SerializationHelpers.readListPoly(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeListPoly(out, payloads)
    }

    override fun <T> accept(visitor: IDataPayloadVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun getPayloadId(): String? {
        return null
    }

    override fun getPayloadType(): Int {
        return IDataPayload.PAYLOAD_TYPE_MULTI
    }

    override fun getLength(): Long {
        var len = 0L
        val en = payloads.iterator()
        while (en.hasNext()) {
            val payload = en.next() as IDataPayload
            len += payload.getLength()
        }
        return len
    }
}
