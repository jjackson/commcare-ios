package org.commcare.core.network

import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.InputStream

/**
 * Response Factory for OkHTTP Response
 */
object OkHTTPResponseMockFactory {

    @JvmStatic
    fun createResponse(responseCode: Int): Response<ResponseBody> {
        return createResponse(responseCode, "")
    }

    @JvmStatic
    fun createResponse(responseCode: Int, headers: Headers): Response<ResponseBody> {
        return createResponse(responseCode, null, headers)
    }

    @JvmStatic
    fun createResponse(responseCode: Int, inputStream: InputStream): Response<ResponseBody> {
        val responseBody = FakeResponseBody(inputStream)
        return createResponse(responseCode, responseBody)
    }

    private fun createResponse(responseCode: Int, body: String): Response<ResponseBody> {
        val responseBody = ResponseBody.create(null, body)
        return createResponse(responseCode, responseBody)
    }

    private fun createResponse(responseCode: Int, responseBody: ResponseBody?): Response<ResponseBody> {
        return createResponse(responseCode, responseBody, null)
    }

    private fun createResponse(
        responseCode: Int,
        responseBody: ResponseBody?,
        headers: Headers?
    ): Response<ResponseBody> {
        val responseBuilder = okhttp3.Response.Builder()
            .code(responseCode)
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())

        return if (responseCode < 400) {
            if (headers != null) {
                responseBuilder.headers(headers)
            }
            responseBuilder.message("OK")
            Response.success(responseBody, responseBuilder.build())
        } else {
            responseBuilder.message("Response.error()")
            Response.error(responseBody!!, responseBuilder.build())
        }
    }
}
