package org.commcare.core.interfaces

import java.io.IOException
import java.io.InputStream

interface ResponseStreamAccessor {
    @Throws(IOException::class)
    fun getResponseStream(): InputStream

    @Throws(IOException::class)
    fun getErrorResponseStream(): InputStream?

    fun getApiVersion(): String?
}
