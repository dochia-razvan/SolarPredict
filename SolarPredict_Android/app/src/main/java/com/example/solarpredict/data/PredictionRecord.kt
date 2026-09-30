package com.example.solarpredict.data

/**
 * One saved prediction (history / favorites). Mirrors the Firestore document
 * written by save_prediction.
 */
data class PredictionRecord(
    val id: String,
    val timestampMillis: Long,
    val p10: Double,
    val p50: Double,
    val p90: Double,
    val unit: String = "Wh",
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationName: String = "",
    val locationAddress: String = "",
    val panelName: String = "",
    val panelAreaM2: Double? = null,
    val panelEfficiency: Double? = null,
    val cloudCover: Int? = null,
    val rainMm: Double? = null,
    val snowMm: Double? = null,
    val isFavorite: Boolean = false,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

enum class WeatherCondition(val label: String) {
    CLEAR("Clear Sunny"),
    PARTLY_CLOUDY("Partly Cloudy"),
    MOSTLY_CLOUDY("Mostly Cloudy"),
    OVERCAST("Overcast"),
    RAIN("Rain Showers"),
    SNOW("Snowfall"),
    UNKNOWN("—"),
}

fun PredictionRecord.deriveCondition(): WeatherCondition {
    if ((snowMm ?: 0.0) > 0.0) return WeatherCondition.SNOW
    if ((rainMm ?: 0.0) > 0.0) return WeatherCondition.RAIN
    val c = cloudCover ?: return WeatherCondition.UNKNOWN
    return when {
        c < 20 -> WeatherCondition.CLEAR
        c < 50 -> WeatherCondition.PARTLY_CLOUDY
        c < 80 -> WeatherCondition.MOSTLY_CLOUDY
        else -> WeatherCondition.OVERCAST
    }
}
