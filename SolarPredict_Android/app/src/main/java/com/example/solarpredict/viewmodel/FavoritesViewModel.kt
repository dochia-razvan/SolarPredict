package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PredictionRecord
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.isGuest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val isLoading: Boolean = false,
    val items: List<PredictionRecord> = emptyList(),
    val error: String? = null,
    val isGuest: Boolean = false,
)

class FavoritesViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState(isGuest = isGuest()))
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null, isGuest = isGuest()) }
        viewModelScope.launch {
            try {
                val items = repository.getFavorites()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not load favorites")
                }
            }
        }
    }

    fun removeFavorite(record: PredictionRecord) {
        val prev = _uiState.value.items
        _uiState.update { it.copy(items = prev.filterNot { p -> p.id == record.id }) }
        viewModelScope.launch {
            try {
                repository.setFavorite(record.id, false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(items = prev, error = "Could not remove: ${e.localizedMessage}")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FavoritesViewModel(PredictionsRepository()) }
        }
    }
}
