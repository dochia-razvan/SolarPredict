package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PanelConfig
import com.example.solarpredict.data.PredictionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPanelUiState(
    val id: String? = null,
    val name: String = "",
    val efficiencyPercent: String = "",
    val areaM2: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    // field errors
    val nameError: String? = null,
    val efficiencyError: String? = null,
    val areaError: String? = null,
)

class EditPanelViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditPanelUiState())
    val uiState: StateFlow<EditPanelUiState> = _uiState.asStateFlow()

    /** Loads an existing panel by id (or starts blank when [id] is null). */
    fun load(id: String?) {
        if (id.isNullOrBlank()) {
            _uiState.update { EditPanelUiState() }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val panels = repository.getPanels()
                val p = panels.firstOrNull { it.id == id }
                if (p == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Panel not found") }
                } else {
                    _uiState.update {
                        it.copy(
                            id = p.id,
                            name = p.name,
                            efficiencyPercent = p.efficiencyPercent.cleanString(),
                            areaM2 = p.areaM2.cleanString(),
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Load failed")
                }
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, nameError = null, error = null) }
    fun onEfficiencyChange(v: String) = _uiState.update { it.copy(efficiencyPercent = v, efficiencyError = null, error = null) }
    fun onAreaChange(v: String) = _uiState.update { it.copy(areaM2 = v, areaError = null, error = null) }

    fun save(onSaved: () -> Unit) {
        val s = _uiState.value
        val nameErr = if (s.name.isBlank()) "Required" else null
        val eff = s.efficiencyPercent.trim().toDoubleOrNull()
        val effErr = when {
            s.efficiencyPercent.isBlank() -> "Required"
            eff == null || eff <= 0.0 || eff > 100.0 -> "Between 0 and 100"
            else -> null
        }
        val area = s.areaM2.trim().toDoubleOrNull()
        val areaErr = when {
            s.areaM2.isBlank() -> "Required"
            area == null || area <= 0.0 -> "Enter a positive number"
            else -> null
        }
        if (nameErr != null || effErr != null || areaErr != null) {
            _uiState.update {
                it.copy(
                    nameError = nameErr,
                    efficiencyError = effErr,
                    areaError = areaErr,
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val panel = PanelConfig(
                id = s.id,
                name = s.name.trim(),
                areaM2 = area!!,
                efficiencyPercent = eff!!,
            )
            try {
                if (s.id == null) {
                    repository.addPanel(panel)
                } else {
                    repository.updatePanel(panel)
                }
                _uiState.update { it.copy(isSaving = false, saved = true) }
                onSaved()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.localizedMessage ?: "Save failed")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { EditPanelViewModel(PredictionsRepository()) }
        }
    }
}

private fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this).trimEnd('0').trimEnd('.')
