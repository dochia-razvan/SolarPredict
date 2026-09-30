package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.SavedLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationScreenUiState(
    val isLoadingSaved: Boolean = false,
    val savedLocations: List<SavedLocation> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
)

class LocationScreenViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationScreenUiState())
    val uiState: StateFlow<LocationScreenUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoadingSaved = true, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.getLocations()
                _uiState.update { it.copy(isLoadingSaved = false, savedLocations = items) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingSaved = false) }
            }
        }
    }

    /** Saves a new location. Returns success/failure via [onResult]. */
    fun saveLocation(location: SavedLocation, onResult: (Boolean, String?) -> Unit) {
        if (_uiState.value.savedLocations.any { it.name.equals(location.name, ignoreCase = true) }) {
            onResult(false, "A location with this name already exists.")
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val saved = repository.addLocation(location)
                _uiState.update {
                    it.copy(
                        saving = false,
                        savedLocations = it.savedLocations + saved,
                    )
                }
                onResult(true, null)
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false) }
                onResult(false, e.localizedMessage ?: "Could not save")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LocationScreenViewModel(PredictionsRepository()) }
        }
    }
}
