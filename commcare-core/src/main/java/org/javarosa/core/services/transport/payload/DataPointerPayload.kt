package org.javarosa.core.services.transport.payload

import org.javarosa.core.data.IDataPointer
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream

/**
 * A payload for a Pointer to some data.
 *
 * @author Clayton Sims
 * @date Dec 29, 2008
 */
class DataPointerPayload : IDataPayload {
    private lateinit var pointer: IDataPointer

    /**
     * Note: Only useful for serialization.
     */
    constructor()

    constructor(pointer: IDataPointer) {
        this.pointer = pointer
    }

    override fun <T> accept(visitor: IDataPayloadVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun getLength(): Long {
        //Unimplemented. This method will eventually leave the contract
        return pointer.getLength()
    }

    override fun getPayloadId(): String? {
        return pointer.getDisplayText()
    }

    @Throws(PlatformIOException::class)
    override fun getPayloadStream(): InputStream {
        return pointer.getDataStream()
    }

    override fun getPayloadType(): Int {
        val display = pointer.getDisplayText()
        if (display == null || display.lastIndexOf('.') == -1) {
            //uhhhh....?
            return IDataPayload.PAYLOAD_TYPE_TEXT
        }

        val ext = display.substring(display.lastIndexOf('.') + 1)

        if (ext == "jpg" || ext == "jpeg") {
            return IDataPayload.PAYLOAD_TYPE_JPG
        }

        return IDataPayload.PAYLOAD_TYPE_JPG
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        pointer = ExtUtil.read(`in`, ExtWrapTagged(), pf) as IDataPointer
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(pointer))
    }
}
