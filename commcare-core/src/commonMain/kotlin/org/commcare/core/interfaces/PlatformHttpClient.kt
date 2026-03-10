package org.commcare.core.interfaces

/**
 * Cross-platform HTTP client interface.
 * Replaces OkHttp/Retrofit for KMP compatibility.
 *
 * On JVM, wraps OkHttp. On iOS, wraps URLSession.
 */
interface PlatformHttpClient {
    fun execute(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null
)

data class HttpResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val errorBody: ByteArray? = null
)

/**
 * Factory function to create a platform-specific HTTP client.
 */
expect fun createHttpClient(): PlatformHttpClient
