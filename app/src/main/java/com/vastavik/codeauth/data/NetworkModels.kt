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

    // Dynamic Service Badge — server now sends explicit fields per new spec
    @SerialName("targetService") val targetService: String? = null,
    @SerialName("target_service") val targetServiceAlt: String? = null,
    @SerialName("serverDomain") val serverDomain: String? = null,
    @SerialName("server_domain") val serverDomainAlt: String? = null,
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

    fun resolvedServerDomain(): String = serverDomain
        ?: serverDomainAlt
        ?: server
        ?: service
        ?: targetServiceAlt
        ?: ""

    fun resolvedTargetService(): String = targetService ?: targetServiceAlt ?: ""

    // Badge identification per spec: check targetService or serverDomain contains "screen"
    fun isTigerVnc(): Boolean {
        val t = resolvedTargetService()
        if (t.equals("TigerVNC", ignoreCase = true)) return true
        if (t.contains("tiger", ignoreCase = true)) return true
        if (resolvedServerDomain().contains("screen", ignoreCase = true)) return true
        if (server?.contains("screen", ignoreCase = true) == true) return true
        return false
    }

    fun isVsCode(): Boolean {
        val t = resolvedTargetService()
        if (t.equals("VS Code", ignoreCase = true)) return true
        if (t.contains("vs", ignoreCase = true) && t.contains("code", ignoreCase = true)) return true
        if (resolvedServerDomain().contains("code", ignoreCase = true)) return true
        if (server?.contains("code", ignoreCase = true) == true) return true
        return false
    }

    // Legacy helpers for backward compat
    fun isVncScreen(): Boolean = isTigerVnc()
    fun isCodeServer(): Boolean = isVsCode()

    fun resolvedBadge(): String = when {
        isTigerVnc() -> "TIGER VNC"
        isVsCode() -> "VS CODE"
        resolvedTargetService().isNotBlank() -> resolvedTargetService().uppercase()
        resolvedServerDomain().isNotBlank() -> resolvedServerDomain().uppercase().let { if (it.contains("SCREEN")) "TIGER VNC" else if (it.contains("CODE")) "VS CODE" else it }
        else -> "UNKNOWN"
    }

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
