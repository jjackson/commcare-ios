package org.commcare.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthenticationInterceptor : Interceptor {

    private var credential: String? = null
    var enforceSecureEndpoint: Boolean = false

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // if credentials are null, proceed without authenticating
        if (credential == null) {
            return chain.proceed(original)
        }

        // Throw an exception if we are sending an authenticated request over HTTP
        if (enforceSecureEndpoint && !original.isHttps && credential != null) {
            throw PlainTextPasswordException()
        }

        val request = original.newBuilder()
            .header("Authorization", credential!!)
            .build()

        return chain.proceed(request)
    }

    fun setCredential(credential: String?) {
        this.credential = credential
    }

    class PlainTextPasswordException : IOException()
}
