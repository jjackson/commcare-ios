package org.javarosa.core.services.transport.payload

import org.javarosa.core.data.IDataPointer
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
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

    @Throws(IOException::class)
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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        pointer = ExtUtil.read(`in`, ExtWrapTagged(), pf) as IDataPointer
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(pointer))
    }
}
