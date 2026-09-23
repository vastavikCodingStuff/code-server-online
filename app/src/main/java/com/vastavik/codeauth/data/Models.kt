package com.vastavik.codeauth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// QR payload: {"server":"...", "sessionId":"..."}
@Serializable
data class QrPayload(
    val server: String? = null,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("session_id") val sessionIdAlt: String? = null
) {
    fun resolvedSessionId(): String = sessionId.ifBlank { sessionIdAlt ?: "" }
}

// POST /api/app/approve
@Serializable
data class ApproveRequest(
    val secret: String,
    val sessionId: String,
    val deviceName: String
)

@Serializable
data class ApproveResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null
)

// GET /api/app/devices -> list of sessions
@Serializable
data class DeviceSession(
    val id: String = "",
    @SerialName("tokenId") val tokenId: String? = null,
    @SerialName("deviceName") val deviceName: String? = null,
    @SerialName("browserAgent") val browserAgent: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,
    @SerialName("ip") val ip: String? = null,
    @SerialName("ipAddress") val ipAddress: String? = null,
    @SerialName("loginTime") val loginTime: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("lastActive") val lastActive: String? = null
) {
    fun resolvedId(): String = tokenId ?: id
    fun resolvedDeviceName(): String = deviceName ?: browserAgent ?: userAgent ?: "Unknown Device"
    fun resolvedIp(): String = ip ?: ipAddress ?: "—"
    fun resolvedTime(): String = loginTime ?: createdAt ?: lastActive ?: "—"
}

// POST /api/app/revoke
@Serializable
data class RevokeRequest(
    val tokenId: String
)

@Serializable
data class RevokeResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class DevicesResponse(
    val devices: List<DeviceSession>? = null,
    val sessions: List<DeviceSession>? = null,
    val data: List<DeviceSession>? = null
) {
    fun resolvedList(): List<DeviceSession> = devices ?: sessions ?: data ?: emptyList()
}
