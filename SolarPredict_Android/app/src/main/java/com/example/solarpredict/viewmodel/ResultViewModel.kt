package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PanelConfig
import com.example.solarpredict.data.PredictionResult
import com.example.solarpredict.data.PredictionsRepository
import com.example.solarpredict.data.SavedLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

enum class TimeRange { NEXT_24H, NEXT_7D, NEXT_14D, CUSTOM }

data class CustomRange(
    val start: LocalDateTime,
    val end: LocalDateTime,
)

data class ResultUiState(
    val isLoadingPrediction: Boolean = false,
    val prediction: PredictionResult? = null,
    val timeRange: TimeRange = TimeRange.NEXT_24H,
    val customRange: CustomRange? = null,
    val savedDocId: String? = null,
    val isFavorite: Boolean = false,
    val isToggling: Boolean = false,
    val error: String? = null,
)

class ResultViewModel(
    private val repository: PredictionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    /**
     * Runs the prediction for the currently-selected time range.
     *
     * Saves to Firestore history *only the first time* the user lands on the
     * Result screen — subsequent range changes (Next 7d, custom span, etc.)
     * just re-fetch and reuse the same saved document. That avoids polluting
     * history with one row per range tap, while still letting the user
     * favorite the original prediction.
     */
    fun loadAndSave(
        location: SavedLocation,
        panel: PanelConfig,
        range: TimeRange = _uiState.value.timeRange,
        custom: CustomRange? = _uiState.value.customRange,
    ) {
        val (df, dt) = computeDateRange(range, custom)
        val isFirstLoad = _uiState.value.savedDocId.isNullOrBlank()
        _uiState.update {
            it.copy(
                isLoadingPrediction = true,
                error = null,
                timeRange = range,
                customRange = custom,
                prediction = null,
            )
        }
        viewModelScope.launch {
            try {
                val result = repository.predictEnergyRange(
                    lat = location.lat,
                    lng = location.lng,
                    suprafataUser = panel.areaM2,
                    eficientaUser = panel.efficiencyPercent / 100.0,
                    dateFrom = df,
                    dateTo = dt,
                )
                val docId: String = if (isFirstLoad) {
                    runCatching {
                        repository.savePrediction(result, location, panel)
                    }.getOrNull().orEmpty()
                } else {
                    _uiState.value.savedDocId.orEmpty()
                }
                _uiState.update {
                    it.copy(
                        isLoadingPrediction = false,
                        prediction = result,
                        savedDocId = docId,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPrediction = false,
                        error = e.localizedMessage ?: "Prediction failed",
                    )
                }
            }
        }
    }

    fun setTimeRange(range: TimeRange, custom: CustomRange? = null) {
        _uiState.update { it.copy(timeRange = range, customRange = custom ?: it.customRange) }
    }

    fun toggleFavorite() {
        val s = _uiState.value
        val docId = s.savedDocId ?: return
        if (s.isToggling) return
        val newValue = !s.isFavorite
        _uiState.update { it.copy(isToggling = true, isFavorite = newValue) }
        viewModelScope.launch {
            try {
                repository.setFavorite(docId, newValue)
                _uiState.update { it.copy(isToggling = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isToggling = false,
                        isFavorite = !newValue,
                        error = e.localizedMessage ?: "Could not update favorite",
                    )
                }
            }
        }
    }

    private fun computeDateRange(range: TimeRange, custom: CustomRange?): Pair<String, String> {
        val today = LocalDate.now()
        return when (range) {
            // 24h spans today + tomorrow because the next 24 hours likely cross midnight.
            TimeRange.NEXT_24H -> today.toString() to today.plusDays(1).toString()
            // 7 calendar days: today + next 6 = 7 days inclusive.
            TimeRange.NEXT_7D -> today.toString() to today.plusDays(6).toString()
            // 14 calendar days: today + next 13 = 14 days inclusive.
            TimeRange.NEXT_14D -> today.toString() to today.plusDays(13).toString()
            TimeRange.CUSTOM -> {
                if (custom != null) {
                    custom.start.toLocalDate().toString() to custom.end.toLocalDate().toString()
                } else {
                    // CUSTOM with no explicit range = degrade gracefully to a
                    // 24-hour window. Returning today, today made the chart
                    // empty because the filter looks at now -> now+24h.
                    today.toString() to today.plusDays(1).toString()
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ResultViewModel(PredictionsRepository()) }
        }
    }
}
