package org.javarosa.core.services.transport.payload

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

/**
 * A ByteArrayPayload is a simple payload consisting of a
 * byte array.
 *
 * @author Clayton Sims
 */
class ByteArrayPayload : IDataPayload {
    private lateinit var payload: ByteArray
    private var id: String? = null
    private var type: Int = 0

    /**
     * Note: Only useful for serialization.
     */
    @Suppress("unused")
    constructor()

    /**
     * @param payload The byte array for this payload.
     * @param id      An optional id identifying the payload
     * @param type    The type of data for this byte array
     */
    constructor(payload: ByteArray, id: String?, type: Int) {
        this.payload = payload
        this.id = id
        this.type = type
    }

    /**
     * @param payload The byte array for this payload.
     */
    constructor(payload: ByteArray) {
        this.payload = payload
        this.id = null
        this.type = IDataPayload.PAYLOAD_TYPE_XML
    }

    override fun getPayloadStream(): InputStream {
        return ByteArrayInputStream(payload)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val length = `in`.readInt()
        if (length > 0) {
            this.payload = ByteArray(length)
            `in`.readFully(this.payload)
        }
        id = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        out.writeInt(payload.size)
        if (payload.isNotEmpty()) {
            out.write(payload)
        }
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(id))
    }

    override fun <T> accept(visitor: IDataPayloadVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun getPayloadId(): String? {
        return id
    }

    override fun getPayloadType(): Int {
        return type
    }

    override fun getLength(): Long {
        return payload.size.toLong()
    }
}
