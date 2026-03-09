package org.javarosa.core.services.transport.payload

import org.javarosa.core.util.MultiInputStream
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Vector

/**
 * @author Clayton Sims
 * @date Dec 18, 2008
 */
class MultiMessagePayload : IDataPayload {
    /** IDataPayload  */
    @JvmField
    var payloads: Vector<Any?> = Vector()

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
        payloads.addElement(payload)
    }

    /**
     * @return A vector object containing each IDataPayload in this payload.
     */
    fun getPayloads(): Vector<Any?> {
        return payloads
    }

    @Throws(IOException::class)
    override fun getPayloadStream(): InputStream {
        val bigStream = MultiInputStream()
        val en = payloads.elements()
        while (en.hasMoreElements()) {
            val payload = en.nextElement() as IDataPayload
            bigStream.addStream(payload.getPayloadStream())
        }
        bigStream.prepare()
        return bigStream
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        payloads = ExtUtil.read(`in`, ExtWrapListPoly(), pf) as Vector<Any?>
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapListPoly(payloads))
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
        val en = payloads.elements()
        while (en.hasMoreElements()) {
            val payload = en.nextElement() as IDataPayload
            len += payload.getLength()
        }
        return len
    }
}
