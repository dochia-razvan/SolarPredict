package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.SavedLocation
import com.example.solarpredict.data.isGuest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationsListUiState(
    val isLoading: Boolean = false,
    val items: List<SavedLocation> = emptyList(),
    val error: String? = null,
    val isGuest: Boolean = false,
)

class LocationsListViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationsListUiState(isGuest = isGuest()))
    val uiState: StateFlow<LocationsListUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null, isGuest = isGuest()) }
        viewModelScope.launch {
            try {
                val items = repository.getLocations()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not load locations")
                }
            }
        }
    }

    fun delete(location: SavedLocation) {
        val id = location.id ?: return
        val prev = _uiState.value.items
        _uiState.update { it.copy(items = prev.filterNot { l -> l.id == id }) }
        viewModelScope.launch {
            try {
                repository.deleteLocation(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(items = prev, error = "Could not delete: ${e.localizedMessage}")
                }
            }
        }
    }

    fun add(location: SavedLocation, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.addLocation(location)
                refresh()
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Could not save location")
            }
        }
    }

    fun update(location: SavedLocation, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateLocation(location)
                refresh()
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Could not update location")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LocationsListViewModel(PredictionsRepository()) }
        }
    }
}
