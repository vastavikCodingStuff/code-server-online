package com.vastavik.codeauth.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypted storage for VPS domain + secret key.
 * Uses Jetpack Security EncryptedSharedPreferences with AES256_GCM + AES256_SIV.
 * file: SecurePrefs.kt
 */
class SecurePrefs private constructor(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<AppConfig> = _configFlow.asStateFlow()

    fun loadConfig(): AppConfig = AppConfig(
        vpsDomain = prefs.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN,
        secretKey = prefs.getString(KEY_SECRET, DEFAULT_SECRET) ?: DEFAULT_SECRET
    )

    fun saveConfig(domain: String, secret: String) {
        prefs.edit()
            .putString(KEY_DOMAIN, domain.trim().removeSuffix("/"))
            .putString(KEY_SECRET, secret.trim())
            .apply()
        _configFlow.value = loadConfig()
    }

    fun getDomain(): String = prefs.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
    fun getSecret(): String = prefs.getString(KEY_SECRET, DEFAULT_SECRET) ?: DEFAULT_SECRET

    fun isConfigured(): Boolean = getSecret().isNotBlank() && getDomain().isNotBlank()

    companion object {
        private const val PREF_NAME = "codeauth_encrypted_prefs"
        private const val KEY_DOMAIN = "vps_domain"
        private const val KEY_SECRET = "secret_key"

        const val DEFAULT_DOMAIN = "https://code.vastaviklearning.online"
        const val DEFAULT_SECRET = "ChangeThisToASecretHighEntropyKey123!"

        @Volatile
        private var INSTANCE: SecurePrefs? = null

        fun getInstance(context: Context): SecurePrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePrefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}

data class AppConfig(
    val vpsDomain: String = SecurePrefs.DEFAULT_DOMAIN,
    val secretKey: String = SecurePrefs.DEFAULT_SECRET
)
