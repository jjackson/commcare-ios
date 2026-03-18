@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.modern.reference

import kotlinx.cinterop.*
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream
import org.javarosa.core.reference.Reference
import org.javarosa.core.util.externalizable.PlatformIOException
import platform.Foundation.*
import platform.darwin.*
import platform.posix.memcpy

/**
 * iOS implementation of HTTP Reference using NSURLSession.
 * Equivalent to JavaHttpReference on JVM.
 */
class IosHttpReference(private val uri: String) : Reference {

    @Throws(PlatformIOException::class)
    override fun doesBinaryExist(): Boolean = true

    @Throws(PlatformIOException::class)
    override fun getOutputStream(): PlatformOutputStream {
        throw PlatformIOException("Http references are read only!")
    }

    @Throws(PlatformIOException::class)
    override fun getStream(): PlatformInputStream {
        val url = NSURL.URLWithString(uri)
            ?: throw PlatformIOException("Invalid URL: $uri")

        val request = NSMutableURLRequest(uRL = url).apply {
            setHTTPMethod("GET")
            setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
            setTimeoutInterval(60.0)
        }

        val semaphore = dispatch_semaphore_create(0)
        var responseData: NSData? = null
        var urlResponse: NSHTTPURLResponse? = null
        var requestError: NSError? = null

        NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            responseData = data
            urlResponse = response as? NSHTTPURLResponse
            requestError = error
            dispatch_semaphore_signal(semaphore)
        }.resume()

        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

        if (requestError != null) {
            throw PlatformIOException("HTTP request failed: ${requestError!!.localizedDescription}")
        }

        val httpResponse = urlResponse
            ?: throw PlatformIOException("No HTTP response received for $uri")

        val statusCode = httpResponse.statusCode.toInt()
        if (statusCode !in 200..299) {
            throw PlatformIOException("HTTP $statusCode for $uri")
        }

        val data = responseData ?: throw PlatformIOException("No data received for $uri")
        val bytes = data.toByteArray()
        return NsDataInputStream(bytes)
    }

    override fun getURI(): String = uri

    override fun isReadOnly(): Boolean = true

    @Throws(PlatformIOException::class)
    override fun remove() {
        throw PlatformIOException("Http references are read only!")
    }

    override fun getLocalURI(): String = uri
}

private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

private class NsDataInputStream(private val data: ByteArray) : PlatformInputStream() {
    private var pos = 0

    override fun read(): Int {
        return if (pos < data.size) data[pos++].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return -1
        val count = minOf(len, data.size - pos)
        data.copyInto(b, off, pos, pos + count)
        pos += count
        return count
    }

    override fun available(): Int = data.size - pos
}
