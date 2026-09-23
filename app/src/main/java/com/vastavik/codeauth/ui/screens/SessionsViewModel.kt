package com.vastavik.codeauth.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastavik.codeauth.data.ApiService
import com.vastavik.codeauth.data.DeviceSession
import com.vastavik.codeauth.data.SecurePrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SessionsViewModel.kt — Live Device Management & Kill-Switch
 * - Fetches List<DeviceSession> (raw JSON Array) via ApiService
 * - Instant revocation: optimistically remove with animation, POST /api/app/revoke {tokenId: session.id}
 * - Snackbar "Session revoked. Browser disconnected." + background refresh
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

    private val _snackbar = MutableSharedFlow<String>(replay = 0)
    val snackbarFlow: SharedFlow<String> = _snackbar.asSharedFlow()

    init {
        refresh()
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
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = ex.message ?: "Failed to fetch sessions") }
            }
        }
    }

    fun retry() = refresh()

    /**
     * Instant Revocation Flow per spec:
     * - Optimistically remove card with animation
     * - POST /api/app/revoke with header x-app-secret and body {"tokenId": session.id}
     * - Snackbar "Session revoked. Browser disconnected."
     * - Background refresh
     */
    fun revokeSession(session: DeviceSession) {
        // Spec mandates {"tokenId": session.id} — use resolvedId which covers id/tokenId
        val tokenId = session.resolvedId().ifBlank { session.id }
        if (tokenId.isBlank()) return

        val snapshot = _uiState.value.sessions

        // Optimistically remove with animation
        _uiState.update { cur ->
            cur.copy(
                sessions = cur.sessions.filterNot { it.resolvedId() == tokenId },
                revokingId = tokenId,
                error = null
            )
        }

        viewModelScope.launch {
            val secret = securePrefs.getSecret()
            val result = ApiService.revokeSession(secret = secret, tokenId = tokenId)
            result.onSuccess { resp ->
                val ok = resp.success != false && resp.error == null
                if (ok) {
                    _snackbar.emit("Session revoked. Browser disconnected.")
                    _uiState.update { it.copy(revokingId = null) }
                    // Trigger background refresh without loading spinner
                    launch {
                        val bg = ApiService.getActiveDevices(secret)
                        bg.onSuccess { list -> _uiState.update { it.copy(sessions = list) } }
                    }
                } else {
                    // Revert on server-reported failure
                    _uiState.update { it.copy(sessions = snapshot, revokingId = null, error = resp.error ?: resp.message ?: "Revoke failed") }
                    _snackbar.emit(resp.error ?: "Revoke failed")
                }
            }.onFailure { ex ->
                // Revert optimistically removed card
                _uiState.update { it.copy(sessions = snapshot, revokingId = null, error = ex.message ?: "Network error revoking") }
                _snackbar.emit(ex.message ?: "Network error revoking")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
