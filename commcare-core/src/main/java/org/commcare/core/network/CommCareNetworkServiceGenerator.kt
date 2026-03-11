package org.commcare.core.network

import org.javarosa.core.util.ListMultimap
import org.commcare.core.services.CommCarePreferenceManagerFactory
import org.commcare.util.LogTypes
import org.javarosa.core.model.utils.DateUtils.HOUR_IN_MS
import org.javarosa.core.services.Logger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import org.javarosa.core.util.externalizable.PlatformIOException
import java.text.SimpleDateFormat
import org.javarosa.core.model.utils.PlatformDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.jvm.JvmStatic

/**
 * Provides an instance of CommCareNetworkService.
 * We have declared everything static in this class as we want to use the same objects (OkHttpClient, Retrofit, ...)
 * throughout the app to just open one socket connection that handles all the request and responses.
 */
object CommCareNetworkServiceGenerator {

    const val CURRENT_DRIFT = "current_drift"
    const val MAX_DRIFT_SINCE_LAST_HEARTBEAT = "max_drift_since_last_heartbeat"

    // Retrofit needs a base url to generate an instance but since our apis are fully dynamic it's not getting used.
    private const val BASE_URL = "http://example.url/"

    private var queryParams: ListMultimap<String, String> = ListMultimap()

    private val builder: Retrofit.Builder = Retrofit.Builder().baseUrl(BASE_URL)

    private val redirectionInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 301) {
            val newUrl = response.header("Location")
            if (!isValidRedirect(request.url, newUrl?.toHttpUrlOrNull())) {
                Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Invalid redirect from ${request.url} to $newUrl")
                throw PlatformIOException("Invalid redirect from secure server to insecure server")
            }
        }
        response
    }

    // Retrofit doesn't support multimaps @Querymap, so add the params on fly instead
    private val queryParamsInterceptor = Interceptor { chain ->
        val urlBuilder = chain.request().url.newBuilder()
        for (entry in queryParams.entries()) {
            urlBuilder.addQueryParameter(entry.key, entry.value)
        }
        val urlWithQueryParams = urlBuilder.build()
        val request = chain.request().newBuilder().url(urlWithQueryParams).build()
        chain.proceed(request)
    }

    private val driftInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val commCarePreferenceManager = CommCarePreferenceManagerFactory.getCommCarePreferenceManager()
        if (commCarePreferenceManager != null) {
            val serverDate = response.header("date")
            try {
                val serverTimeInMillis = SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH
                ).parse(serverDate).time
                val now = PlatformDate().time
                var currentDrift = (now - serverTimeInMillis) / HOUR_IN_MS
                commCarePreferenceManager.putLong(CURRENT_DRIFT, currentDrift)
                val maxDriftSinceLastHeartbeat = commCarePreferenceManager.getLong(MAX_DRIFT_SINCE_LAST_HEARTBEAT, 0)
                currentDrift *= if (currentDrift < 0) -1 else 1 // make it positive to calculate max drift
                if (currentDrift > maxDriftSinceLastHeartbeat) {
                    commCarePreferenceManager.putLong(MAX_DRIFT_SINCE_LAST_HEARTBEAT, currentDrift)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        response
    }

    private val authenticationInterceptor = AuthenticationInterceptor()

    private val httpClient: OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(ModernHttpRequester.CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(ModernHttpRequester.CONNECTION_SO_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .addNetworkInterceptor(redirectionInterceptor)
        .addInterceptor(queryParamsInterceptor)
        .addInterceptor(authenticationInterceptor)
        .addInterceptor(driftInterceptor)
        .followRedirects(true)

    private var retrofit: Retrofit = builder.client(
        httpClient.retryOnConnectionFailure(true).build()
    ).build()

    private var noRetryRetrofit: Retrofit = builder.client(
        httpClient.retryOnConnectionFailure(false).build()
    ).build()

    @JvmStatic
    fun customizeRetrofitSetup(config: HttpBuilderConfig) {
        retrofit = builder.client(
            config.performCustomConfig(httpClient.retryOnConnectionFailure(true)).build()
        ).build()
        noRetryRetrofit = builder.client(
            config.performCustomConfig(httpClient.retryOnConnectionFailure(false)).build()
        ).build()
    }

    @JvmStatic
    fun createCommCareNetworkService(
        credential: String?,
        enforceSecureEndpoint: Boolean,
        retry: Boolean,
        params: ListMultimap<String, String>
    ): CommCareNetworkService {
        queryParams = params
        authenticationInterceptor.setCredential(credential)
        authenticationInterceptor.enforceSecureEndpoint = enforceSecureEndpoint
        return if (retry) {
            retrofit.create(CommCareNetworkService::class.java)
        } else {
            noRetryRetrofit.create(CommCareNetworkService::class.java)
        }
    }

    @JvmStatic
    fun createNoAuthCommCareNetworkService(): CommCareNetworkService {
        return createCommCareNetworkService(null, false, true, ListMultimap())
    }

    @JvmStatic
    fun createNoAuthCommCareNetworkService(params: ListMultimap<String, String>): CommCareNetworkService {
        return createCommCareNetworkService(null, false, true, params)
    }

    private fun isValidRedirect(url: okhttp3.HttpUrl, newUrl: okhttp3.HttpUrl?): Boolean {
        // unless it's https, don't worry about it
        if (url.scheme != "https") {
            return true
        }

        // If https, verify that we're on the same server.
        // Not being so means we got redirected from a secure link to a
        // different link, which isn't acceptable for now.
        return newUrl != null && url.host == newUrl.host
    }
}
