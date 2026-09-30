package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    /** Call from SignUp's redirect: shows a one-shot success banner. */
    fun setInfoMessage(text: String) {
        _uiState.update { it.copy(infoMessage = text) }
    }

    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (!validate(s)) return
        run(onSuccess) { authRepository.signIn(s.email.trim(), s.password) }
    }

    fun continueAsGuest(onSuccess: () -> Unit) {
        run(onSuccess) { authRepository.signInAnonymously() }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "Enter your email address.")
            return
        }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email.trim())
                onResult(true, "Password reset email sent to ${email.trim()}.")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Could not send reset email.")
            }
        }
    }

    private fun run(onSuccess: () -> Unit, action: suspend () -> Any) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Authentication failed")
                }
            }
        }
    }

    private fun validate(s: LoginUiState): Boolean {
        if (s.email.isBlank() || s.password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password are required") }
            return false
        }
        if (s.password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return false
        }
        return true
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LoginViewModel(AuthRepository(Firebase.auth))
            }
        }
    }
}
