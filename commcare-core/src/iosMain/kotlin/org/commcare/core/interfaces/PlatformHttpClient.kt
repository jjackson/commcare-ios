package org.commcare.core.interfaces

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLConnection
import platform.Foundation.NSURLResponse
import platform.Foundation.create
import platform.Foundation.sendSynchronousRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.posix.memcpy
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError

/**
 * iOS HTTP client using NSURLConnection (synchronous).
 */
class IosHttpClient : PlatformHttpClient {

    @OptIn(ExperimentalForeignApi::class)
    override fun execute(request: HttpRequest): HttpResponse {
        val url = NSURL.URLWithString(request.url)
            ?: throw RuntimeException("Invalid URL: ${request.url}")

        val nsRequest = NSMutableURLRequest.requestWithURL(url)
        nsRequest.setHTTPMethod(request.method.uppercase())

        for ((name, value) in request.headers) {
            nsRequest.setValue(value, forHTTPHeaderField = name)
        }

        if (request.body != null && request.body.isNotEmpty()) {
            val nsData = request.body.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = request.body.size.toULong())
            }
            nsRequest.setHTTPBody(nsData)
        }

        if (request.contentType != null) {
            nsRequest.setValue(request.contentType, forHTTPHeaderField = "Content-Type")
        }

        memScoped {
            val responsePtr = alloc<ObjCObjectVar<NSURLResponse?>>()
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            @Suppress("DEPRECATION")
            val responseData = NSURLConnection.sendSynchronousRequest(
                nsRequest,
                returningResponse = responsePtr.ptr,
                error = errorPtr.ptr
            )

            val error = errorPtr.value
            if (error != null) {
                return HttpResponse(
                    code = error.code.toInt(),
                    headers = emptyMap(),
                    body = null,
                    errorBody = error.localizedDescription.encodeToByteArray()
                )
            }

            val httpResponse = responsePtr.value as? NSHTTPURLResponse
            val statusCode = httpResponse?.statusCode?.toInt() ?: 0

            val headers = mutableMapOf<String, String>()
            httpResponse?.allHeaderFields?.forEach { (key, value) ->
                if (key is String && value is String) {
                    headers[key] = value
                }
            }

            val bodyBytes = responseData?.let { data ->
                val size = data.length.toInt()
                if (size == 0) ByteArray(0)
                else {
                    val bytes = ByteArray(size)
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), data.bytes, data.length)
                    }
                    bytes
                }
            }

            return HttpResponse(
                code = statusCode,
                headers = headers,
                body = if (statusCode in 200..299) bodyBytes else null,
                errorBody = if (statusCode !in 200..299) bodyBytes else null
            )
        }
    }
}

actual fun createHttpClient(): PlatformHttpClient = IosHttpClient()
