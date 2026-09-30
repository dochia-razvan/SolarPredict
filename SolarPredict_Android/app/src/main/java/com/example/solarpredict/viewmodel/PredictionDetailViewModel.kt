package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PredictionRecord
import com.example.solarpredict.data.PredictionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PredictionDetailUiState(
    val isLoading: Boolean = false,
    val record: PredictionRecord? = null,
    val notFound: Boolean = false,
    val error: String? = null,
    val isToggling: Boolean = false,
)

/**
 * Loads a single saved prediction by id (history or favorites).
 *
 * Why this exists: opening "Result" for a saved row needs the full record,
 * but we don't want to roundtrip a separate Cloud Function call. We reuse
 * `get_history` (which is already cached on the client by the list VMs)
 * and look the record up by id locally.
 */
class PredictionDetailViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredictionDetailUiState())
    val uiState: StateFlow<PredictionDetailUiState> = _uiState.asStateFlow()

    fun load(id: String) {
        if (id.isBlank()) {
            _uiState.update { it.copy(notFound = true) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, notFound = false) }
        viewModelScope.launch {
            try {
                // Pull the most recent N records (covers both history and
                // favorites since favorites is a subset of history) and
                // find the one with matching id.
                val all = repository.getHistory(limit = 200)
                val match = all.firstOrNull { it.id == id }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        record = match,
                        notFound = match == null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Could not load prediction",
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val s = _uiState.value
        val rec = s.record ?: return
        if (s.isToggling) return
        val newValue = !rec.isFavorite
        _uiState.update {
            it.copy(
                isToggling = true,
                record = rec.copy(isFavorite = newValue),
            )
        }
        viewModelScope.launch {
            try {
                repository.setFavorite(rec.id, newValue)
                _uiState.update { it.copy(isToggling = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isToggling = false,
                        record = rec, // revert
                        error = e.localizedMessage ?: "Could not update favorite",
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PredictionDetailViewModel(PredictionsRepository()) }
        }
    }
}
