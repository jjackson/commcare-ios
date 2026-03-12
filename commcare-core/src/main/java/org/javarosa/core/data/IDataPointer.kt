package org.javarosa.core.data

import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformIOException
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
    @Throws(PlatformIOException::class)
    fun getData(): ByteArray

    /**
     * Get the data from the underlying storage.
     */
    @Throws(PlatformIOException::class)
    fun getDataStream(): PlatformInputStream

    /**
     * Deletes the underlying data from storage.
     */
    fun deleteData(): Boolean

    /**
     * @return Gets the length of the data payload
     */
    fun getLength(): Long
}
