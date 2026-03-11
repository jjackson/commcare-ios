package org.commcare.core.interfaces

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream

interface ResponseStreamAccessor {
    @Throws(PlatformIOException::class)
    fun getResponseStream(): PlatformInputStream

    @Throws(PlatformIOException::class)
    fun getErrorResponseStream(): PlatformInputStream?

    fun getApiVersion(): String?
}
