package com.vastavik.codeauth.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit2 + Kotlinx Serialization client.
 * file: NetworkClient.kt: Retrofit builder + API interface
 */
interface CodeAuthApi {

    @POST("api/app/approve")
    suspend fun approve(
        @Body body: ApproveRequest
    ): ApproveResponse

    @GET("api/app/devices")
    suspend fun getDevices(
        @Header("x-app-secret") secret: String
    ): DevicesResponse

    @POST("api/app/revoke")
    suspend fun revoke(
        @Header("x-app-secret") secret: String,
        @Body body: RevokeRequest
    ): RevokeResponse
}

object NetworkClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        prettyPrint = false
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApi: CodeAuthApi? = null

    fun getApi(baseUrl: String): CodeAuthApi {
        val normalized = baseUrl.trim().removeSuffix("/") + "/"
        if (cachedApi != null && cachedBaseUrl == normalized) return cachedApi!!

        synchronized(this) {
            if (cachedApi != null && cachedBaseUrl == normalized) return cachedApi!!
            val retrofit = Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val api = retrofit.create(CodeAuthApi::class.java)
            cachedBaseUrl = normalized
            cachedApi = api
            return api
        }
    }

    /** Convenience: one-shot approve without caching concerns outside. */
    suspend fun approveSession(
        baseUrl: String,
        secret: String,
        sessionId: String,
        deviceName: String
    ): Result<ApproveResponse> = runCatching {
        getApi(baseUrl).approve(ApproveRequest(secret, sessionId, deviceName))
    }

    suspend fun fetchDevices(
        baseUrl: String,
        secret: String
    ): Result<List<DeviceSession>> = runCatching {
        val resp = getApi(baseUrl).getDevices(secret)
        resp.resolvedList()
    }

    suspend fun revokeSession(
        baseUrl: String,
        secret: String,
        tokenId: String
    ): Result<RevokeResponse> = runCatching {
        getApi(baseUrl).revoke(secret, RevokeRequest(tokenId))
    }
}
