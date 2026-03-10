package org.commcare.core.interfaces

import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream

interface ResponseStreamAccessor {
    @Throws(PlatformIOException::class)
    fun getResponseStream(): InputStream

    @Throws(PlatformIOException::class)
    fun getErrorResponseStream(): InputStream?

    fun getApiVersion(): String?
}
