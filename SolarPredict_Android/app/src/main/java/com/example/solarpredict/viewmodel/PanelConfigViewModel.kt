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

/** Form state for either an "Add new" card or a "Use saved (editable)" card. */
data class PanelFormState(
    val name: String = "",
    val efficiencyPercent: String = "",
    val areaM2: String = "",
    val saveAfterCalculate: Boolean = false,
    /** When true: overwrites the source saved panel (when [editingSavedId] is set). */
    val overwriteSaved: Boolean = false,
    /** id of the saved panel currently being edited; null when adding new. */
    val editingSavedId: String? = null,
)

data class PanelConfigUiState(
    val isLoadingPanels: Boolean = false,
    val savedPanels: List<PanelConfig> = emptyList(),
    val mode: Mode = Mode.None,
    val form: PanelFormState = PanelFormState(),
    val isCalculating: Boolean = false,
    val error: String? = null,
) {
    enum class Mode { None, AddNew, UseSaved }
}

class PanelConfigViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PanelConfigUiState())
    val uiState: StateFlow<PanelConfigUiState> = _uiState.asStateFlow()

    init { loadSavedPanels() }

    fun loadSavedPanels() {
        _uiState.update { it.copy(isLoadingPanels = true, error = null) }
        viewModelScope.launch {
            try {
                val panels = repository.getPanels()
                _uiState.update { it.copy(isLoadingPanels = false, savedPanels = panels) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingPanels = false) }
            }
        }
    }

    fun startAddNew() {
        _uiState.update {
            it.copy(mode = PanelConfigUiState.Mode.AddNew, form = PanelFormState(), error = null)
        }
    }

    fun selectSavedPanel(id: String) {
        val p = _uiState.value.savedPanels.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                mode = PanelConfigUiState.Mode.UseSaved,
                form = PanelFormState(
                    name = p.name,
                    efficiencyPercent = p.efficiencyPercent.cleanString(),
                    areaM2 = p.areaM2.cleanString(),
                    overwriteSaved = false,
                    editingSavedId = p.id,
                ),
                error = null,
            )
        }
    }

    fun cancelMode() {
        _uiState.update {
            it.copy(mode = PanelConfigUiState.Mode.None, form = PanelFormState(), error = null)
        }
    }

    fun onNameChange(v: String) = touchForm { it.copy(name = v) }
    fun onEfficiencyChange(v: String) = touchForm { it.copy(efficiencyPercent = v) }
    fun onAreaChange(v: String) = touchForm { it.copy(areaM2 = v) }
    fun onSaveAfterCalculateChange(v: Boolean) = touchForm { it.copy(saveAfterCalculate = v) }
    fun onOverwriteSavedChange(v: Boolean) = touchForm { it.copy(overwriteSaved = v) }

    private fun touchForm(transform: (PanelFormState) -> PanelFormState) {
        _uiState.update { it.copy(form = transform(it.form), error = null) }
    }

    /**
     * Resolves the active panel and returns it (or null with an error message).
     *
     * Name is only required when the user is saving the panel
     * (Add New + saveAfterCalculate, or any UseSaved mode).
     */
    fun resolvePanelOrError(): PanelConfig? {
        val s = _uiState.value
        val f = s.form
        val name = f.name.trim()
        val area = f.areaM2.toDoubleOrNull()
        val eff = f.efficiencyPercent.toDoubleOrNull()
        val nameRequired = (s.mode == PanelConfigUiState.Mode.AddNew && f.saveAfterCalculate) ||
            (s.mode == PanelConfigUiState.Mode.UseSaved)
        when {
            nameRequired && name.isBlank() -> {
                _uiState.update { it.copy(error = "Panel name is required to save") }; return null
            }
            area == null || area <= 0 -> {
                _uiState.update { it.copy(error = "Enter a valid area (m²)") }; return null
            }
            eff == null || eff <= 0 || eff > 100 -> {
                _uiState.update { it.copy(error = "Efficiency must be between 0 and 100") }; return null
            }
        }
        // Uniqueness when the name will be persisted.
        if (s.mode == PanelConfigUiState.Mode.AddNew && f.saveAfterCalculate &&
            s.savedPanels.any { it.name.equals(name, ignoreCase = true) }
        ) {
            _uiState.update { it.copy(error = "A panel with this name already exists") }
            return null
        }
        if (s.mode == PanelConfigUiState.Mode.UseSaved &&
            s.savedPanels.any { it.name.equals(name, ignoreCase = true) && it.id != f.editingSavedId }
        ) {
            _uiState.update { it.copy(error = "A panel with this name already exists") }
            return null
        }
        return PanelConfig(
            id = f.editingSavedId,
            name = name.ifBlank { "Custom panel" },
            areaM2 = area!!,
            efficiencyPercent = eff!!,
        )
    }

    /** Persists the panel (if requested) and returns the resolved PanelConfig via [onResolved]. */
    fun calculate(
        onResolved: (PanelConfig) -> Unit,
    ) {
        val panel = resolvePanelOrError() ?: return
        val s = _uiState.value

        _uiState.update { it.copy(isCalculating = true, error = null) }
        viewModelScope.launch {
            try {
                // Persist if requested
                val persisted = when (s.mode) {
                    PanelConfigUiState.Mode.AddNew -> {
                        if (s.form.saveAfterCalculate) {
                            runCatching { repository.addPanel(panel) }.getOrNull() ?: panel
                        } else panel
                    }
                    PanelConfigUiState.Mode.UseSaved -> {
                        if (s.form.overwriteSaved && panel.id != null) {
                            runCatching { repository.updatePanel(panel) }
                            panel
                        } else panel
                    }
                    PanelConfigUiState.Mode.None -> panel
                }
                _uiState.update { it.copy(isCalculating = false) }
                onResolved(persisted)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCalculating = false, error = e.localizedMessage ?: "Failed")
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PanelConfigViewModel(PredictionsRepository()) }
        }
    }
}

private fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this).trimEnd('0').trimEnd('.')
