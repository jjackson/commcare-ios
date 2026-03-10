package org.commcare.core.interfaces

/**
 * iOS HTTP client.
 * Will use NSURLSession via Kotlin/Native interop.
 */
class IosHttpClient : PlatformHttpClient {
    override fun execute(request: HttpRequest): HttpResponse {
        TODO("iOS HTTP client requires Foundation NSURLSession cinterop")
    }
}

actual fun createHttpClient(): PlatformHttpClient = IosHttpClient()
