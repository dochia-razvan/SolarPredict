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

data class HistoryUiState(
    val isLoading: Boolean = false,
    val items: List<PredictionRecord> = emptyList(),
    val error: String? = null,
)

class HistoryViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.getHistory(limit = 100)
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not load history")
                }
            }
        }
    }

    fun delete(record: PredictionRecord) {
        val previous = _uiState.value.items
        _uiState.update { it.copy(items = previous.filterNot { r -> r.id == record.id }) }
        viewModelScope.launch {
            try {
                repository.deletePrediction(record.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        items = previous,
                        error = "Could not delete: ${e.localizedMessage ?: "unknown error"}",
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HistoryViewModel(PredictionsRepository()) }
        }
    }
}
