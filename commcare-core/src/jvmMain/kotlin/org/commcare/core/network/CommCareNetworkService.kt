package org.commcare.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Streaming
import retrofit2.http.Url

interface CommCareNetworkService {

    @Streaming
    @GET
    fun makeGetRequest(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Call<ResponseBody>

    @Streaming
    @Multipart
    @POST
    fun makeMultipartPostRequest(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Part files: List<MultipartBody.Part>
    ): Call<ResponseBody>

    @Streaming
    @POST
    fun makePostRequest(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Call<ResponseBody>

    @PUT
    fun makePutRequest(
        @Url url: String,
        @Body body: RequestBody
    ): Call<ResponseBody>

    @Streaming
    @DELETE
    fun makeDeleteRequest(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Call<ResponseBody>
}
