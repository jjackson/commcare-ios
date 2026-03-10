package org.commcare.core.interfaces

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class JvmHttpClient : PlatformHttpClient {
    private val client = OkHttpClient()

    override fun execute(request: HttpRequest): HttpResponse {
        val builder = Request.Builder().url(request.url)

        for ((name, value) in request.headers) {
            builder.header(name, value)
        }

        val body = request.body
        when (request.method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(
                (body ?: ByteArray(0)).toRequestBody(request.contentType?.toMediaTypeOrNull())
            )
            "PUT" -> builder.put(
                (body ?: ByteArray(0)).toRequestBody(request.contentType?.toMediaTypeOrNull())
            )
            "DELETE" -> {
                if (body != null) {
                    builder.delete(body.toRequestBody(request.contentType?.toMediaTypeOrNull()))
                } else {
                    builder.delete()
                }
            }
        }

        val response = client.newCall(builder.build()).execute()
        return HttpResponse(
            code = response.code,
            headers = response.headers.toMap()
                .mapValues { it.value.firstOrNull() ?: "" },
            body = response.body?.bytes(),
            errorBody = null
        )
    }
}

private fun okhttp3.Headers.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    for (i in 0 until size) {
        result.getOrPut(name(i)) { mutableListOf() }.add(value(i))
    }
    return result
}

actual fun createHttpClient(): PlatformHttpClient = JvmHttpClient()
