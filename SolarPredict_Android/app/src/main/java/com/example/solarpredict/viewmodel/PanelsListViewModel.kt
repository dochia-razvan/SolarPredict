package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PanelConfig
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.isGuest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PanelsListUiState(
    val isLoading: Boolean = false,
    val items: List<PanelConfig> = emptyList(),
    val error: String? = null,
    val isGuest: Boolean = false,
)

class PanelsListViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PanelsListUiState(isGuest = isGuest()))
    val uiState: StateFlow<PanelsListUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null, isGuest = isGuest()) }
        viewModelScope.launch {
            try {
                val items = repository.getPanels()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not load panels")
                }
            }
        }
    }

    fun delete(panel: PanelConfig) {
        val id = panel.id ?: return
        val previous = _uiState.value.items
        _uiState.update { it.copy(items = previous.filterNot { p -> p.id == id }) }
        viewModelScope.launch {
            try {
                repository.deletePanel(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(items = previous, error = "Could not delete: ${e.localizedMessage}")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PanelsListViewModel(PredictionsRepository()) }
        }
    }
}
