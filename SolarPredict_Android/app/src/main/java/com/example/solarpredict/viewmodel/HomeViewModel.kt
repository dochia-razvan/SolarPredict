package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PredictionRecord
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.isGuest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class HomeUiState(
    val greeting: String = "Hello",
    val userName: String = "",
    val isGuest: Boolean = false,
    val isLoading: Boolean = false,
    val lastPrediction: PredictionRecord? = null,
    val recent: List<PredictionRecord> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val repository: PredictionsRepository,
    userEmail: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            greeting = greetingForHour(LocalTime.now().hour),
            userName = if (isGuest()) "Guest" else nameFromEmail(userEmail),
            isGuest = isGuest(),
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        // Re-evaluate the *current* user identity on every refresh: the
        // ViewModel may outlive sign-out/sign-in transitions, so the cached
        // userName/isGuest from init() can go stale. Pull straight from
        // FirebaseAuth so a guest who just linked an email gets their real
        // name + isGuest=false on the next resume.
        val currentUser = Firebase.auth.currentUser
        val freshIsGuest = currentUser?.isAnonymous == true
        val baselineName = if (freshIsGuest) "Guest" else nameFromEmail(currentUser?.email)
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                isGuest = freshIsGuest,
                userName = baselineName,
            )
        }
        viewModelScope.launch {
            try {
                if (!freshIsGuest) {
                    runCatching { repository.getUserProfile() }.getOrNull()?.let { profile ->
                        if (profile.username.isNotBlank()) {
                            _uiState.update { it.copy(userName = profile.username) }
                        }
                    }
                }
                val items = repository.getHistory(limit = 20)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastPrediction = items.firstOrNull(),
                        recent = items.drop(1).take(3),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not load history")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = PredictionsRepository(),
                    userEmail = Firebase.auth.currentUser?.email,
                )
            }
        }
    }
}

internal fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

internal fun nameFromEmail(email: String?): String =
    email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "there"

internal fun initialFromEmail(email: String?): String =
    email?.firstOrNull()?.uppercase() ?: "?"
