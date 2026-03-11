package org.javarosa.core.data

import org.javarosa.core.util.externalizable.Externalizable
import java.io.IOException
import java.io.InputStream
import kotlin.jvm.Throws

/**
 * A data pointer representing a pointer to a (usually) larger object in memory.
 *
 * @author Cory Zue
 */
interface IDataPointer : Externalizable {

    /**
     * Get a display string that represents this data.
     */
    fun getDisplayText(): String

    /**
     * Get the data from the underlying storage. This should maybe be a stream instead of a byte[]
     */
    @Throws(IOException::class)
    fun getData(): ByteArray

    /**
     * Get the data from the underlying storage.
     */
    @Throws(IOException::class)
    fun getDataStream(): InputStream

    /**
     * Deletes the underlying data from storage.
     */
    fun deleteData(): Boolean

    /**
     * @return Gets the length of the data payload
     */
    fun getLength(): Long
}
