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

data class SignUpUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    // Real-time field errors
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
)

class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onUsernameChange(v: String) = _uiState.update {
        it.copy(username = v, usernameError = validateUsername(v), error = null)
    }

    fun onEmailChange(v: String) = _uiState.update {
        it.copy(email = v, emailError = validateEmail(v), error = null)
    }

    fun onPasswordChange(v: String) = _uiState.update {
        it.copy(
            password = v,
            passwordError = validatePassword(v),
            confirmPasswordError = if (it.confirmPassword.isNotEmpty() && it.confirmPassword != v) {
                "Passwords do not match"
            } else null,
            error = null,
        )
    }

    fun onConfirmPasswordChange(v: String) = _uiState.update {
        it.copy(
            confirmPassword = v,
            confirmPasswordError = if (v != it.password) "Passwords do not match" else null,
            error = null,
        )
    }

    fun submit(onAccountCreated: (email: String) -> Unit) {
        val s = _uiState.value
        val u = validateUsername(s.username)
        val e = validateEmail(s.email)
        val p = validatePassword(s.password)
        val c = if (s.confirmPassword != s.password) "Passwords do not match" else null
        if (u != null || e != null || p != null || c != null) {
            _uiState.update {
                it.copy(usernameError = u, emailError = e, passwordError = p, confirmPasswordError = c)
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // Try linking-from-anonymous first if a guest is currently signed in.
                val current = authRepository.currentUser
                val user = if (current != null && current.isAnonymous) {
                    authRepository.linkAnonymousWithEmail(s.email.trim(), s.password)
                } else {
                    authRepository.register(s.email.trim(), s.password)
                }
                // Save username to Firestore /users/{uid}/profile/info
                runCatching { repository.saveUserProfile(s.username.trim()) }
                // Sign out after registration so the user comes back via Sign In (no auto-login).
                authRepository.signOut()
                _uiState.update { it.copy(isLoading = false) }
                onAccountCreated(s.email.trim())
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = ex.localizedMessage ?: "Sign up failed")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SignUpViewModel(AuthRepository(Firebase.auth), PredictionsRepository())
            }
        }

        fun validateUsername(v: String): String? = when {
            v.isBlank() -> "Username is required"
            v.length < 3 -> "Username must be at least 3 characters"
            v.length > 30 -> "Username must be at most 30 characters"
            !v.matches(Regex("^[A-Za-z0-9_]+$")) -> "Letters, digits, and _ only"
            else -> null
        }

        private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun validateEmail(v: String): String? = when {
            v.isBlank() -> "Email is required"
            !EMAIL_RE.matches(v) -> "Invalid email format"
            else -> null
        }

        fun validatePassword(v: String): String? = when {
            v.length < 8 -> "Password must be at least 8 characters"
            !v.any { it.isUpperCase() } -> "Add at least one uppercase letter"
            !v.any { it.isDigit() } -> "Add at least one number"
            !v.any { !it.isLetterOrDigit() } -> "Add at least one special character"
            else -> null
        }
    }
}
