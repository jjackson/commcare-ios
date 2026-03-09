package org.javarosa.core.services.transport.payload

import org.javarosa.core.util.externalizable.Externalizable
import java.io.IOException
import java.io.InputStream

/**
 * IDataPayload is an interface that specifies a piece of data
 * that will be transmitted over the wire to
 *
 * @author Clayton Sims
 * @date Dec 18, 2008
 */
interface IDataPayload : Externalizable {

    /**
     * Gets the stream for this payload.
     *
     * @return A stream for the data in this payload.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun getPayloadStream(): InputStream

    /**
     * @return A string identifying the contents of the payload
     */
    fun getPayloadId(): String?

    /**
     * @return The type of the data encapsulated by this
     * payload.
     */
    fun getPayloadType(): Int

    /**
     * Visitor pattern accept method.
     *
     * @param visitor The visitor to visit this payload.
     */
    fun <T> accept(visitor: IDataPayloadVisitor<T>): T

    fun getLength(): Long

    companion object {
        /**
         * Data payload codes
         */
        const val PAYLOAD_TYPE_TEXT: Int = 0
        const val PAYLOAD_TYPE_XML: Int = 1
        const val PAYLOAD_TYPE_JPG: Int = 2
        const val PAYLOAD_TYPE_HEADER: Int = 3
        const val PAYLOAD_TYPE_MULTI: Int = 4
        const val PAYLOAD_TYPE_SMS: Int = 5 // sms's are a variant of TEXT having a limit on length.
    }
}
