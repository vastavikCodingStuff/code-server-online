package com.vastavik.codeauth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHubRelease.kt — Model for GitHub Releases API
 * Endpoint: GET https://api.github.com/repos/vastavikCodingStuff/code-server-authenticator/releases/latest
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false
) {
    fun displayVersion(): String = name?.ifBlank { null } ?: tagName
    fun apkAsset(): GitHubAsset? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)
