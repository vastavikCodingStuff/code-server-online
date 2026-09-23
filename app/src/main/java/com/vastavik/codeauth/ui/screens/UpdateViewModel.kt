package com.vastavik.codeauth.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.vastavik.codeauth.BuildConfig
import com.vastavik.codeauth.data.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// Repository configurable via constant or SecurePrefs
object UpdateConfig {
    const val DEFAULT_GITHUB_REPO = "vastavikCodingStuff/code-server-authenticator"
    // Fallback to actual repo where releases exist for testing
    const val FALLBACK_REPO = "vastavikCodingStuff/code-server-online"
    const val GITHUB_API_BASE = "https://api.github.com/"
}

// Retrofit GitHub API
private interface GitHubApi {
    @GET("repos/{repo}/releases/latest")
    suspend fun getLatestRelease(@Path(value = "repo", encoded = true) repo: String): GitHubRelease
}

sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data class UpToDate(val current: String, val latest: GitHubRelease) : UpdateUiState
    data class UpdateAvailable(val current: String, val latest: GitHubRelease) : UpdateUiState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateUiState
    data class Downloaded(val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel(
    private val githubRepo: String = UpdateConfig.DEFAULT_GITHUB_REPO
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val retrofit = Retrofit.Builder()
        .baseUrl(UpdateConfig.GITHUB_API_BASE)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(GitHubApi::class.java)

    private val _uiState: MutableStateFlow<UpdateUiState> = MutableStateFlow(UpdateUiState.Checking)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var latestRelease: GitHubRelease? = null
    private var downloadFile: File? = null

    init {
        checkForUpdate()
    }

    fun checkForUpdate(repo: String = githubRepo) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            try {
                val current = BuildConfig.VERSION_NAME
                val release = try {
                    api.getLatestRelease(repo)
                } catch (e: Exception) {
                    // Fallback to code-server-online if authenticator repo has no releases yet
                    if (repo == UpdateConfig.DEFAULT_GITHUB_REPO) {
                        api.getLatestRelease(UpdateConfig.FALLBACK_REPO)
                    } else throw e
                }
                latestRelease = release
                val latestTag = release.tagName.trim().removePrefix("v")
                val currentClean = current.trim().removePrefix("v")
                val isUpdate = isNewerVersion(latestTag, currentClean)
                _uiState.value = if (isUpdate) {
                    UpdateUiState.UpdateAvailable(current, release)
                } else {
                    UpdateUiState.UpToDate(current, release)
                }
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest == current) return false
        // Simple semantic compare: split by . and compare ints
        fun parse(v: String) = v.split(".", "-").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val l = parse(latest)
        val c = parse(current)
        val max = maxOf(l.size, c.size)
        for (i in 0 until max) {
            val lv = l.getOrNull(i) ?: 0
            val cv = c.getOrNull(i) ?: 0
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return latest != current
    }

    fun downloadAndInstall(context: Context) {
        val release = latestRelease ?: run {
            _uiState.value = UpdateUiState.Error("No release found")
            return
        }
        val asset = release.apkAsset() ?: run {
            _uiState.value = UpdateUiState.Error("No APK asset found in latest release")
            return
        }
        viewModelScope.launch {
            try {
                _uiState.value = UpdateUiState.Downloading(0f, 0, asset.size)
                val file = withContext(Dispatchers.IO) { downloadApk(context, asset.browserDownloadUrl, asset.name) }
                downloadFile = file
                _uiState.value = UpdateUiState.Downloaded(file)
                // Auto-launch installer after download
                installApk(context, file)
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Download failed")
            }
        }
    }

    private suspend fun downloadApk(context: Context, url: String, fileName: String): File = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
            val body = response.body ?: throw Exception("Empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            // Use cacheDir or external files dir for FileProvider
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName.ifBlank { "update.apk" })
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (total > 0) {
                            val progress = (bytesCopied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            launch(Dispatchers.Main) {
                                _uiState.value = UpdateUiState.Downloading(progress, bytesCopied, total)
                            }
                        }
                    }
                }
            }
            outFile
        }
    }

    fun installApk(context: Context, file: File? = null) {
        val targetFile = file ?: downloadFile ?: return
        try {
            // Check install permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                }
            }
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, targetFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = UpdateUiState.Error("Install failed: ${e.message}")
        }
    }

    fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val parser2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = try { parser.parse(iso) } catch (_: Exception) { parser2.parse(iso) }
            SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(date!!)
        } catch (_: Exception) { iso }
    }
}
