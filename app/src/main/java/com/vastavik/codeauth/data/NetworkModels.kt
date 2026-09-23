package com.vastavik.codeauth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NetworkModels.kt — single source of truth for API payloads
 * CRITICAL FIX: GET /api/app/devices returns raw JSON Array List<DeviceSession>, NOT wrapper object
 */

// QR payload: {"server":"code.vastaviklearning.online" | "screen.vastaviklearning.online", "sessionId":"<UUID>"}
@Serializable
data class QrPayload(
    val server: String? = null,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("session_id") val sessionIdAlt: String? = null
) {
    fun resolvedSessionId(): String = sessionId.ifBlank { sessionIdAlt ?: "" }
    fun resolvedServer(): String = server?.trim()?.ifBlank { null } ?: "code.vastaviklearning.online"
}

// POST https://<server>/api/app/approve
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

// GET https://code.vastaviklearning.online/api/app/devices -> returns raw JSON Array
@Serializable
data class DeviceSession(
    val id: String = "",
    @SerialName("tokenId") val tokenId: String? = null,

    // Service discriminator — server can be "code.vastaviklearning.online" or "screen.vastaviklearning.online"
    @SerialName("targetService") val targetService: String? = null,
    @SerialName("target_service") val targetServiceAlt: String? = null,
    @SerialName("server") val server: String? = null,
    @SerialName("service") val service: String? = null,

    @SerialName("deviceName") val deviceName: String? = null,
    @SerialName("device_name") val deviceNameAlt: String? = null,
    @SerialName("browserAgent") val browserAgent: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,

    @SerialName("ip") val ip: String? = null,
    @SerialName("ipAddress") val ipAddress: String? = null,

    @SerialName("loginTime") val loginTime: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("lastActive") val lastActive: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("signInTime") val signInTime: String? = null
) {
    fun resolvedId(): String = tokenId?.ifBlank { null } ?: id.ifBlank { "" }

    fun resolvedTargetService(): String = targetService
        ?: targetServiceAlt
        ?: server
        ?: service
        ?: "code.vastaviklearning.online"

    fun isVncScreen(): Boolean = resolvedTargetService().contains("screen", ignoreCase = true)
    fun isCodeServer(): Boolean = !isVncScreen()

    fun resolvedBadge(): String = if (isVncScreen()) "VNC SCREEN" else "VS CODE"

    fun resolvedDeviceName(): String = deviceName
        ?: deviceNameAlt
        ?: browserAgent
        ?: userAgent
        ?: "Unknown Device"

    fun resolvedIp(): String = ip ?: ipAddress ?: "—"

    fun resolvedTime(): String = loginTime ?: createdAt ?: lastActive ?: timestamp ?: signInTime ?: "—"

    fun resolvedUserAgent(): String? = browserAgent ?: userAgent ?: deviceNameAlt
}

// POST https://code.vastaviklearning.online/api/app/revoke
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

// Deprecated wrapper kept for legacy compat — DO NOT USE for GET /devices (now returns List)
@Serializable
data class DevicesResponse(
    val devices: List<DeviceSession>? = null,
    val sessions: List<DeviceSession>? = null,
    val data: List<DeviceSession>? = null
) {
    fun resolvedList(): List<DeviceSession> = devices ?: sessions ?: data ?: emptyList()
}
