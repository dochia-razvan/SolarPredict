package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.AuthRepository
import com.example.solarpredict.data.PredictionsRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountInfoUiState(
    val email: String = "",
    val username: String = "",
    val originalUsername: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSendingReset: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class AccountInfoViewModel(
    private val authRepository: AuthRepository,
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountInfoUiState(email = authRepository.currentUser?.email.orEmpty())
    )
    val uiState: StateFlow<AccountInfoUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val profile = repository.getUserProfile()
                val u = profile?.username.orEmpty()
                _uiState.update {
                    it.copy(isLoading = false, username = u, originalUsername = u)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onUsernameChange(v: String) =
        _uiState.update { it.copy(username = v, error = null, message = null) }

    fun saveUsername(onSaved: () -> Unit) {
        val s = _uiState.value
        val err = SignUpViewModel.validateUsername(s.username)
        if (err != null) {
            _uiState.update { it.copy(error = err) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                repository.saveUserProfile(s.username.trim())
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        originalUsername = s.username.trim(),
                        message = "Username updated.",
                    )
                }
                onSaved()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.localizedMessage ?: "Save failed")
                }
            }
        }
    }

    fun sendChangePasswordEmail() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "No email associated with this account.") }
            return
        }
        _uiState.update { it.copy(isSendingReset = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update {
                    it.copy(
                        isSendingReset = false,
                        message = "A password reset email has been sent to $email.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSendingReset = false, error = e.localizedMessage ?: "Could not send")
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AccountInfoViewModel(AuthRepository(Firebase.auth), PredictionsRepository())
            }
        }
    }
}
