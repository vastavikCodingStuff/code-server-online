package com.vastavik.codeauth.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastavik.codeauth.data.ApiService
import com.vastavik.codeauth.data.DeviceSession
import com.vastavik.codeauth.data.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SessionsViewModel.kt — Manages active browser sessions
 * - Fetches List<DeviceSession> (raw JSON Array) via ApiService
 * - Handles loading / error / empty states without crash
 * - Revoke with optimistic animated removal
 */
data class SessionsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val sessions: List<DeviceSession> = emptyList(),
    val error: String? = null,
    val revokingId: String? = null
)

class SessionsViewModel(
    private val securePrefs: SecurePrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState(isLoading = true))
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // Observe config changes to auto-refresh
        viewModelScope.launch {
            securePrefs.configFlow.collect {
                // trigger refresh on domain/secret change (debounced via distinct not needed)
            }
        }
    }

    fun refresh(isPull: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !isPull && it.sessions.isEmpty(), isRefreshing = isPull, error = null) }
            val secret = securePrefs.getSecret()
            if (secret.isBlank()) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Secret not configured. Go to Settings.") }
                return@launch
            }
            val result = ApiService.getActiveDevices(secret)
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, sessions = list, error = null) }
            }.onFailure { ex ->
                // Preserve existing list on refresh failure, only set error
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = ex.message ?: "Failed to fetch sessions") }
            }
        }
    }

    fun retry() = refresh()

    fun revokeSession(session: DeviceSession, onSuccess: (() -> Unit)? = null) {
        val id = session.resolvedId()
        if (id.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(revokingId = id) }
            val secret = securePrefs.getSecret()
            val result = ApiService.revokeSession(secret = secret, tokenId = id)
            result.onSuccess { resp ->
                val ok = resp.success != false && resp.error == null
                if (ok) {
                    _uiState.update { cur ->
                        cur.copy(sessions = cur.sessions.filterNot { it.resolvedId() == id }, revokingId = null, error = null)
                    }
                    onSuccess?.invoke()
                } else {
                    _uiState.update { it.copy(revokingId = null, error = resp.error ?: resp.message ?: "Revoke failed") }
                }
            }.onFailure { ex ->
                _uiState.update { it.copy(revokingId = null, error = ex.message ?: "Network error") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
