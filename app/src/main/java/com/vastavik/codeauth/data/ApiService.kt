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
 * ApiService.kt — Retrofit2 + Kotlinx Serialization
 * CRITICAL FIX: GET /api/app/devices returns raw JSON Array List<DeviceSession>
 */
interface VastavikApi {

    @POST("api/app/approve")
    suspend fun approve(
        @Body body: ApproveRequest
    ): ApproveResponse

    // CRITICAL: returns List<DeviceSession> directly (raw array), NOT wrapper
    @GET("api/app/devices")
    suspend fun getActiveDevices(
        @Header("x-app-secret") secret: String
    ): List<DeviceSession>

    @POST("api/app/revoke")
    suspend fun revoke(
        @Header("x-app-secret") secret: String,
        @Body body: RevokeRequest
    ): RevokeResponse
}

object ApiService {

    const val DEFAULT_BASE = "https://code.vastaviklearning.online/"

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
    private var cachedApi: VastavikApi? = null

    fun getApi(baseUrl: String): VastavikApi {
        val normalized = normalizeBase(baseUrl)
        if (cachedApi != null && cachedBaseUrl == normalized) return cachedApi!!
        synchronized(this) {
            if (cachedApi != null && cachedBaseUrl == normalized) return cachedApi!!
            val retrofit = Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val api = retrofit.create(VastavikApi::class.java)
            cachedBaseUrl = normalized
            cachedApi = api
            return api
        }
    }

    private fun normalizeBase(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http")) trimmed.removeSuffix("/") + "/"
        else "https://$trimmed".removeSuffix("/") + "/"
    }

    private fun normalizeServer(server: String): String {
        val s = server.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
        return "https://$s/"
    }

    /** Approve for dynamic server (code or screen) — POST https://<server>/api/app/approve */
    suspend fun approveForServer(
        server: String,
        secret: String,
        sessionId: String,
        deviceName: String
    ): Result<ApproveResponse> = runCatching {
        val base = normalizeServer(server)
        getApi(base).approve(ApproveRequest(secret, sessionId, deviceName))
    }

    /** Legacy helper: approve with full baseUrl */
    suspend fun approveSession(
        baseUrl: String,
        secret: String,
        sessionId: String,
        deviceName: String
    ): Result<ApproveResponse> = runCatching {
        getApi(baseUrl).approve(ApproveRequest(secret, sessionId, deviceName))
    }

    /** Fetch active sessions — GET https://code.vastaviklearning.online/api/app/devices -> List */
    suspend fun getActiveDevices(
        secret: String,
        baseUrl: String = DEFAULT_BASE
    ): Result<List<DeviceSession>> = runCatching {
        getApi(baseUrl).getActiveDevices(secret)
    }

    /** Backward compat wrapper used by old SessionsScreen */
    suspend fun fetchDevices(
        baseUrl: String,
        secret: String
    ): Result<List<DeviceSession>> = getActiveDevices(secret, baseUrl)

    suspend fun revokeSession(
        baseUrl: String = DEFAULT_BASE,
        secret: String,
        tokenId: String
    ): Result<RevokeResponse> = runCatching {
        getApi(baseUrl).revoke(secret, RevokeRequest(tokenId))
    }

    suspend fun revoke(tokenId: String, secret: String): Result<RevokeResponse> =
        revokeSession(DEFAULT_BASE, secret, tokenId)
}

// Backward compatibility: keep NetworkClient alias so old imports still compile
@Deprecated("Use ApiService", ReplaceWith("ApiService"))
typealias CodeAuthApi = VastavikApi

object NetworkClient {
    suspend fun approveSession(baseUrl: String, secret: String, sessionId: String, deviceName: String) =
        ApiService.approveSession(baseUrl, secret, sessionId, deviceName)

    suspend fun fetchDevices(baseUrl: String, secret: String) =
        ApiService.fetchDevices(baseUrl, secret)

    suspend fun revokeSession(baseUrl: String, secret: String, tokenId: String) =
        ApiService.revokeSession(baseUrl, secret, tokenId)

    suspend fun approveForServer(server: String, secret: String, sessionId: String, deviceName: String) =
        ApiService.approveForServer(server, secret, sessionId, deviceName)

    suspend fun getActiveDevices(secret: String, baseUrl: String = ApiService.DEFAULT_BASE) =
        ApiService.getActiveDevices(secret, baseUrl)

    fun getApi(baseUrl: String) = ApiService.getApi(baseUrl)
}
