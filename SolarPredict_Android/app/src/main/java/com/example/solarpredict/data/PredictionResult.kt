package com.example.solarpredict.data

/**
 * Result of a `predict_energy` call.
 *
 * Mode A (current single slot): p10/p50/p90 are 15-min Wh + a `weather` snapshot.
 * Mode B (date range): p10/p50/p90 are totals across the full range; `hourly`
 * has one entry per 15-min slot, and `dailyTotals` aggregates per day.
 */
data class PredictionResult(
    val p10: Double,
    val p50: Double,
    val p90: Double,
    val unit: String = "Wh",
    val timestamp: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val hourly: List<HourlyPoint> = emptyList(),
    val dailyTotals: List<DailyTotal> = emptyList(),
    val weather: Weather? = null,
)

/** One 15-minute bucket of the prediction. */
data class HourlyPoint(
    val timestamp: String,    // ISO 8601 local time, e.g. "2026-04-29T12:15"
    val p10: Double,
    val p50: Double,
    val p90: Double,
    val ghi: Double = 0.0,
    val temp: Double = 0.0,
    val cloudsAll: Int = 0,
)

/** Per-day total energy, summed from all 15-min slots in that day. */
data class DailyTotal(
    val date: String,         // "YYYY-MM-DD"
    val p10TotalWh: Double,
    val p50TotalWh: Double,
    val p90TotalWh: Double,
)

/** Subset of the weather snapshot the UI displays. */
data class Weather(
    val ghi: Double? = null,
    val temp: Double? = null,
    val cloudsAll: Int? = null,
)
