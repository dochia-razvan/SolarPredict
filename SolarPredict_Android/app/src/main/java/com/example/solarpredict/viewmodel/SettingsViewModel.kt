package com.example.solarpredict.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.AuthRepository
import com.example.solarpredict.data.LANG_EN
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.SettingsPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String = "",
    val email: String = "",
    val initial: String = "?",
    val isGuest: Boolean = false,
    val language: String = LANG_EN,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val settingsPreferences: SettingsPreferences,
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            displayName = nameFromEmail(authRepository.currentUser?.email),
            email = authRepository.currentUser?.email.orEmpty(),
            initial = initialFromEmail(authRepository.currentUser?.email),
            isGuest = authRepository.isGuest,
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val lang = settingsPreferences.language.first()
            _uiState.update { it.copy(language = lang) }
            // Pull username from /users/{uid}/profile/info if available
            if (!authRepository.isGuest) {
                runCatching { repository.getUserProfile() }.getOrNull()?.let { profile ->
                    if (profile.username.isNotBlank()) {
                        _uiState.update {
                            it.copy(
                                displayName = profile.username,
                                initial = profile.username.firstOrNull()?.uppercase() ?: "?",
                            )
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(displayName = "Guest", initial = "G") }
            }
        }
    }

    fun setLanguage(tag: String) {
        _uiState.update { it.copy(language = tag) }
        viewModelScope.launch { settingsPreferences.setLanguage(tag) }
    }

    fun signOut(@Suppress("UNUSED_PARAMETER") onSignedOut: () -> Unit) {
        // We deliberately do NOT call onSignedOut() here. The AuthStateListener
        // in AppNavGraph already navigates to Login on sign-out — calling it
        // again from here was racing with the listener and caused a brief
        // flicker on slow devices. Keep the parameter for source compatibility.
        authRepository.signOut()
    }

    companion object {
        fun factoryFor(applicationContext: Context) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    authRepository = AuthRepository(Firebase.auth),
                    settingsPreferences = SettingsPreferences(applicationContext),
                    repository = PredictionsRepository(),
                )
            }
        }
    }
}
