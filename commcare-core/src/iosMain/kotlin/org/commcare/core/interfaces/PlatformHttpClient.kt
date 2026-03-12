@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.core.interfaces

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.*
import platform.posix.memcpy

/**
 * iOS HTTP client using NSURLSession.
 *
 * Uses synchronous semaphore-based approach since the PlatformHttpClient
 * interface requires synchronous execute(). All calls should be made
 * from a background thread to avoid blocking the main thread.
 */
class IosHttpClient : PlatformHttpClient {
    override fun execute(request: HttpRequest): HttpResponse {
        val url = NSURL.URLWithString(request.url)
            ?: throw RuntimeException("Invalid URL: ${request.url}")

        val urlRequest = NSMutableURLRequest(uRL = url).apply {
            setHTTPMethod(request.method)
            setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
            setTimeoutInterval(60.0)

            for ((key, value) in request.headers) {
                setValue(value, forHTTPHeaderField = key)
            }

            if (request.body != null) {
                setHTTPBody(request.body.toNSData())
                if (request.contentType != null) {
                    setValue(request.contentType, forHTTPHeaderField = "Content-Type")
                }
            }
        }

        // Synchronous request using semaphore
        val semaphore = dispatch_semaphore_create(0)
        var responseData: NSData? = null
        var urlResponse: NSHTTPURLResponse? = null
        var requestError: NSError? = null

        NSURLSession.sharedSession.dataTaskWithRequest(urlRequest) { data, response, error ->
            responseData = data
            urlResponse = response as? NSHTTPURLResponse
            requestError = error
            dispatch_semaphore_signal(semaphore)
        }.resume()

        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

        if (requestError != null) {
            throw RuntimeException("HTTP request failed: ${requestError!!.localizedDescription}")
        }

        val httpResponse = urlResponse
            ?: throw RuntimeException("No HTTP response received")

        val statusCode = httpResponse.statusCode.toInt()
        val responseHeaders = mutableMapOf<String, String>()
        val allHeaders = httpResponse.allHeaderFields
        for (key in allHeaders.keys) {
            val headerName = key.toString()
            val headerValue = allHeaders[key]?.toString() ?: ""
            responseHeaders[headerName] = headerValue
        }

        val bodyBytes = responseData?.toByteArray()

        return if (statusCode in 200..299) {
            HttpResponse(
                code = statusCode,
                headers = responseHeaders,
                body = bodyBytes,
                errorBody = null
            )
        } else {
            HttpResponse(
                code = statusCode,
                headers = responseHeaders,
                body = null,
                errorBody = bodyBytes
            )
        }
    }
}

actual fun createHttpClient(): PlatformHttpClient = IosHttpClient()

private fun ByteArray.toNSData(): NSData {
    if (this.isEmpty()) return NSData()
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
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
