package org.commcare.core.network

import org.javarosa.core.util.ListMultimap
import org.commcare.core.interfaces.HttpResponseProcessor
import org.commcare.core.interfaces.ResponseStreamAccessor
import org.commcare.core.network.bitcache.BitCacheFactory
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Make http get/post requests with query params encoded in get url or post
 * body. Delegates response to appropriate response processor callback
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
open class ModernHttpRequester(
    private val cacheDirSetup: BitCacheFactory.CacheDirSetup,
    protected val url: String,
    protected val headers: Map<String, String>,
    private val requestBody: RequestBody?,
    private val parts: List<MultipartBody.Part>?,
    private val commCareNetworkService: CommCareNetworkService,
    private val method: HTTPMethod,
    private val responseProcessor: HttpResponseProcessor?
) {
    private var response: Response<ResponseBody>? = null
    private var currentCall: Call<ResponseBody>? = null

    /**
     * Executes and process the Request using the ResponseProcessor.
     */
    fun makeRequestAndProcess() {
        if (responseProcessor == null) {
            throw IllegalStateException("Please call makeRequest since responseProcessor is null")
        }
        try {
            response = makeRequest()
            val requester = this
            processResponse(responseProcessor, response!!.code(), object : ResponseStreamAccessor {
                /**
                 * Only gets called if response processor is supplied
                 * @return Input Stream from cache
                 * @throws IOException if an io error happens while reading or writing to cache
                 */
                @Throws(IOException::class)
                override fun getResponseStream(): InputStream {
                    return requester.getResponseStream(response!!)
                }

                @Throws(IOException::class)
                override fun getErrorResponseStream(): InputStream? {
                    return requester.getErrorResponseStream(response!!)
                }

                override fun getApiVersion(): String? {
                    return requester.getApiVersion()
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            responseProcessor.handleIOException(e)
        }
    }

    /**
     * Executes the HTTP Request. Can be called directly to bypass response processor.
     *
     * @return Response from the HTTP call
     * @throws IOException if a problem occurred talking to the server.
     */
    @Throws(IOException::class)
    fun makeRequest(): Response<ResponseBody> {
        currentCall = when (method) {
            HTTPMethod.POST -> commCareNetworkService.makePostRequest(url, headers, requestBody!!)
            HTTPMethod.MULTIPART_POST -> commCareNetworkService.makeMultipartPostRequest(url, headers, parts!!)
            HTTPMethod.GET -> commCareNetworkService.makeGetRequest(url, headers)
        }
        return executeAndCheckCaptivePortals(currentCall!!)
    }

    @Throws(IOException::class)
    private fun executeAndCheckCaptivePortals(currentCall: Call<ResponseBody>): Response<ResponseBody> {
        try {
            return currentCall.execute()
        } catch (e: SSLException) {
            // SSLHandshakeException is thrown by the CommcareRequestGenerator on
            // 4.3 devices when the peer certificate is bad.
            //
            // SSLPeerUnverifiedException is thrown by the CommcareRequestGenerator
            // on 2.3 devices when the peer certificate is bad.
            //
            // This may be a real SSL exception associated with the real endpoint server, or this
            // might be a property of the local network.

            if (org.commcare.util.NetworkStatus.isCaptivePortal()) {
                throw CaptivePortalRedirectException()
            }

            // Otherwise just rethrow the original exception. Probably a certificate issue
            // Could be related to local clock issue
            throw e
        }
    }

    /**
     * Writes responseStream to cache and returns it
     * @return Input Stream from cache
     * @throws IOException if an io error happens while reading or writing to cache
     */
    @Throws(IOException::class)
    fun getResponseStream(response: Response<ResponseBody>): InputStream {
        val inputStream = response.body()!!.byteStream()
        return cacheResponse(inputStream, response)
    }

    @Throws(IOException::class)
    private fun cacheResponse(inputStream: InputStream, response: Response<ResponseBody>): InputStream {
        val cache = BitCacheFactory.getCache(cacheDirSetup, getContentLength(response))
        cache.initializeCache()
        val cacheOut = cache.getCacheStream()
        StreamsUtil.writeFromInputToOutputNew(inputStream, cacheOut)
        return cache.retrieveCache()
    }

    @Throws(IOException::class)
    fun getErrorResponseStream(response: Response<ResponseBody>): InputStream? {
        if (response.errorBody() != null) {
            return cacheResponse(response.errorBody()!!.byteStream(), response)
        }
        return null
    }

    fun getApiVersion(): String? {
        return response?.headers()?.get("x-api-current-version")
    }

    fun cancelRequest() {
        currentCall?.cancel()
    }

    companion object {
        /**
         * How long to wait when opening network connection in milliseconds
         */
        @JvmField
        val CONNECTION_TIMEOUT: Int = TimeUnit.MINUTES.toMillis(2).toInt()

        /**
         * How long to wait when receiving data (in milliseconds)
         */
        @JvmField
        val CONNECTION_SO_TIMEOUT: Int = TimeUnit.MINUTES.toMillis(1).toInt()

        @JvmStatic
        fun processResponse(
            responseProcessor: HttpResponseProcessor,
            responseCode: Int,
            streamAccessor: ResponseStreamAccessor
        ) {
            if (responseCode in 200..299) {
                var responseStream: InputStream? = null
                try {
                    try {
                        responseStream = streamAccessor.getResponseStream()
                    } catch (e: IOException) {
                        responseProcessor.handleIOException(e)
                        return
                    }
                    val apiVersion = streamAccessor.getApiVersion()
                    responseProcessor.processSuccess(responseCode, responseStream, apiVersion)
                } finally {
                    StreamsUtil.closeStream(responseStream)
                }
            } else if (responseCode in 400..499) {
                var errorStream: InputStream? = null
                try {
                    errorStream = streamAccessor.getErrorResponseStream()
                    responseProcessor.processClientError(responseCode, errorStream)
                } catch (e: Exception) {
                    Logger.exception("Exception during network error processing", e)
                } finally {
                    StreamsUtil.closeStream(errorStream)
                }
            } else if (responseCode in 500..599) {
                responseProcessor.processServerError(responseCode)
            } else {
                responseProcessor.processOther(responseCode)
            }
        }

        @JvmStatic
        fun getPostBody(inputs: ListMultimap<String, String>): RequestBody {
            val formBodyBuilder = FormBody.Builder()
            for (param in inputs.entries()) {
                formBodyBuilder.add(param.key, param.value)
            }
            return formBodyBuilder.build()
        }

        @JvmStatic
        fun getContentLength(response: Response<*>): Long {
            var contentLength: Long = -1
            val length = getFirstHeader(response, "Content-Length")
            try {
                contentLength = length!!.toLong()
            } catch (e: Exception) {
                // Whatever.
            }
            return contentLength
        }

        @JvmStatic
        fun getFirstHeader(response: Response<*>, headerName: String): String? {
            val headers = response.headers().values(headerName)
            if (headers.isNotEmpty()) {
                return headers[0]
            }
            return null
        }
    }
}
