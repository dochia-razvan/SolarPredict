package com.example.solarpredict.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await

class NotAuthenticatedException :
    IllegalStateException("You must be signed in to use this feature.")

/**
 * One-stop repository for every Cloud Function call. Every method returns
 * plain Kotlin data so the UI layer never sees raw Firebase types.
 *
 * Auth contract: each public method calls [preflightAuth] which (a) throws
 * fast if there's no signed-in user, and (b) primes the SDK's ID-token cache
 * so the auto-attached Authorization header is fresh.
 */
class PredictionsRepository(
    private val functions: FirebaseFunctions = Firebase.functions("europe-west1"),
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private suspend fun preflightAuth() {
        val user = auth.currentUser ?: throw NotAuthenticatedException()
        try {
            val token = user.getIdToken(false).await().token
            if (token.isNullOrBlank()) throw NotAuthenticatedException()
        } catch (e: NotAuthenticatedException) {
            throw e
        } catch (_: Exception) {
            throw NotAuthenticatedException()
        }
    }

    // -----------------------------------------------------------------------
    // predict_energy
    // -----------------------------------------------------------------------

    suspend fun predictEnergyRange(
        lat: Double,
        lng: Double,
        suprafataUser: Double,
        eficientaUser: Double,
        dateFrom: String,
        dateTo: String,
    ): PredictionResult {
        preflightAuth()
        val payload = mapOf<String, Any>(
            "lat" to lat,
            "lng" to lng,
            "suprafata_user" to suprafataUser,
            "eficienta_user" to eficientaUser,
            "date_from" to dateFrom,
            "date_to" to dateTo,
        )
        val result = functions.getHttpsCallable("predict_energy").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
            ?: return PredictionResult(0.0, 0.0, 0.0)
        return data.toPredictionResult()
    }

    // -----------------------------------------------------------------------
    // History
    // -----------------------------------------------------------------------

    suspend fun getHistory(limit: Int = 50): List<PredictionRecord> {
        if (isGuest()) return GuestStore.predictions.value
        preflightAuth()
        val payload = mapOf<String, Any>("limit" to limit)
        val result = functions.getHttpsCallable("get_history").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val items = data["predictions"] as? List<Map<String, Any?>> ?: return emptyList()
        return items.mapNotNull { it.toPredictionRecord() }
    }

    suspend fun deletePrediction(docId: String) {
        if (isGuest()) {
            GuestStore.deletePrediction(docId); return
        }
        preflightAuth()
        functions.getHttpsCallable("delete_prediction")
            .call(mapOf("doc_id" to docId)).await()
    }

    suspend fun savePrediction(
        result: PredictionResult,
        location: SavedLocation,
        panel: PanelConfig,
    ): String {
        if (isGuest()) {
            val rec = PredictionRecord(
                id = "",
                timestampMillis = System.currentTimeMillis(),
                p10 = result.p10,
                p50 = result.p50,
                p90 = result.p90,
                unit = result.unit,
                locationLat = location.lat,
                locationLng = location.lng,
                locationName = location.name,
                locationAddress = location.address,
                panelName = panel.name,
                panelAreaM2 = panel.areaM2,
                panelEfficiency = panel.efficiencyPercent / 100.0,
                cloudCover = result.weather?.cloudsAll,
                isFavorite = false,
                dateFrom = result.dateFrom,
                dateTo = result.dateTo,
            )
            return GuestStore.addPrediction(rec).id
        }
        preflightAuth()
        val payload = mutableMapOf<String, Any>(
            "prediction_result" to mapOf(
                "p10" to result.p10,
                "p50" to result.p50,
                "p90" to result.p90,
                "unit" to result.unit,
                "weather" to (result.weather?.let {
                    mapOf(
                        "GHI" to (it.ghi ?: 0.0),
                        "temp" to (it.temp ?: 0.0),
                        "clouds_all" to (it.cloudsAll ?: 0),
                    )
                } ?: emptyMap<String, Any>()),
            ),
            "location" to mapOf(
                "lat" to location.lat,
                "lng" to location.lng,
                "name" to location.name,
                "address" to location.address,
            ),
            "panel_config" to mapOf(
                "name" to panel.name,
                "area_m2" to panel.areaM2,
                "efficiency" to panel.efficiencyPercent / 100.0,
            ),
        )
        result.dateFrom?.let { payload["date_from"] = it }
        result.dateTo?.let { payload["date_to"] = it }
        val callResult = functions.getHttpsCallable("save_prediction").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = callResult.data as? Map<String, Any?> ?: return ""
        return (data["doc_id"] as? String).orEmpty()
    }

    // -----------------------------------------------------------------------
    // Panels
    // -----------------------------------------------------------------------

    suspend fun getPanels(): List<PanelConfig> {
        if (isGuest()) return GuestStore.panels.value
        preflightAuth()
        val result = functions.getHttpsCallable("get_panels").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val list = data["panels"] as? List<Map<String, Any?>> ?: return emptyList()
        return list.mapNotNull { it.toPanelConfig() }
    }

    /** Adds a new panel. Throws if a panel with that name already exists. */
    suspend fun addPanel(panel: PanelConfig): PanelConfig {
        if (isGuest()) {
            if (GuestStore.panels.value.any { it.name.equals(panel.name, ignoreCase = true) }) {
                error("A panel named '${panel.name}' already exists.")
            }
            return GuestStore.addPanel(panel)
        }
        preflightAuth()
        val payload = mapOf<String, Any>(
            "name" to panel.name,
            "area_m2" to panel.areaM2,
            "efficiency" to panel.efficiencyPercent / 100.0,
        )
        val callResult = functions.getHttpsCallable("save_panel").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = callResult.data as? Map<String, Any?>
        val id = (data?.get("doc_id") as? String).orEmpty()
        return panel.copy(id = id)
    }

    suspend fun updatePanel(panel: PanelConfig) {
        val id = panel.id ?: error("Panel id is required for update")
        if (isGuest()) {
            if (!GuestStore.updatePanel(panel)) error("Panel not found")
            return
        }
        preflightAuth()
        val payload = mapOf<String, Any>(
            "doc_id" to id,
            "name" to panel.name,
            "area_m2" to panel.areaM2,
            "efficiency" to panel.efficiencyPercent / 100.0,
        )
        functions.getHttpsCallable("update_panel").call(payload).await()
    }

    suspend fun deletePanel(id: String) {
        if (isGuest()) {
            GuestStore.removePanel(id); return
        }
        preflightAuth()
        functions.getHttpsCallable("delete_panel").call(mapOf("doc_id" to id)).await()
    }

    // -----------------------------------------------------------------------
    // Locations
    // -----------------------------------------------------------------------

    suspend fun getLocations(): List<SavedLocation> {
        if (isGuest()) return GuestStore.locations.value
        preflightAuth()
        val result = functions.getHttpsCallable("get_locations").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val list = data["locations"] as? List<Map<String, Any?>> ?: return emptyList()
        return list.mapNotNull { it.toSavedLocation() }
    }

    suspend fun addLocation(location: SavedLocation): SavedLocation {
        if (isGuest()) {
            if (GuestStore.locations.value.any { it.name.equals(location.name, ignoreCase = true) }) {
                error("A location named '${location.name}' already exists.")
            }
            return GuestStore.addLocation(location)
        }
        preflightAuth()
        val payload = mapOf<String, Any>(
            "name" to location.name,
            "lat" to location.lat,
            "lng" to location.lng,
            "address" to location.address,
        )
        val callResult = functions.getHttpsCallable("save_location").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = callResult.data as? Map<String, Any?>
        val id = (data?.get("doc_id") as? String).orEmpty()
        return location.copy(id = id)
    }

    suspend fun updateLocation(location: SavedLocation) {
        val id = location.id ?: error("Location id is required for update")
        if (isGuest()) {
            if (!GuestStore.updateLocation(location)) error("Location not found")
            return
        }
        preflightAuth()
        val payload = mapOf<String, Any>(
            "doc_id" to id,
            "name" to location.name,
            "lat" to location.lat,
            "lng" to location.lng,
            "address" to location.address,
        )
        functions.getHttpsCallable("update_location").call(payload).await()
    }

    suspend fun deleteLocation(id: String) {
        if (isGuest()) {
            GuestStore.removeLocation(id); return
        }
        preflightAuth()
        functions.getHttpsCallable("delete_location").call(mapOf("doc_id" to id)).await()
    }

    // -----------------------------------------------------------------------
    // User profile
    // -----------------------------------------------------------------------

    suspend fun saveUserProfile(username: String) {
        if (isGuest()) return // Guests have no Firestore profile
        preflightAuth()
        functions.getHttpsCallable("save_user_profile")
            .call(mapOf("username" to username)).await()
    }

    suspend fun getUserProfile(): UserProfile? {
        if (isGuest()) return null
        preflightAuth()
        val result = functions.getHttpsCallable("get_user_profile").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return null
        @Suppress("UNCHECKED_CAST")
        val p = data["profile"] as? Map<String, Any?> ?: return null
        return UserProfile(
            username = p["username"] as? String ?: "",
            email = p["email"] as? String ?: "",
        )
    }

    // -----------------------------------------------------------------------
    // Favorites
    // -----------------------------------------------------------------------

    suspend fun getFavorites(): List<PredictionRecord> {
        if (isGuest()) return GuestStore.predictions.value.filter { it.isFavorite }
        preflightAuth()
        val result = functions.getHttpsCallable("get_favorites").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val items = data["predictions"] as? List<Map<String, Any?>> ?: return emptyList()
        return items.mapNotNull { it.toPredictionRecord() }
    }

    suspend fun setFavorite(predictionId: String, value: Boolean) {
        if (isGuest()) {
            GuestStore.toggleFavorite(predictionId, value); return
        }
        preflightAuth()
        val name = if (value) "add_favorite" else "remove_favorite"
        functions.getHttpsCallable(name)
            .call(mapOf("prediction_id" to predictionId)).await()
    }
}

data class UserProfile(val username: String, val email: String)

// ===========================================================================
// JSON parsing helpers
// ===========================================================================

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toPanelConfig(): PanelConfig? {
    val area = this["area_m2"].asDouble() ?: return null
    val effDec = this["efficiency"].asDouble() ?: return null
    return PanelConfig(
        id = this["id"] as? String,
        name = (this["name"] as? String) ?: "Unnamed",
        areaM2 = area,
        efficiencyPercent = effDec * 100.0,
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toSavedLocation(): SavedLocation? {
    val lat = this["lat"].asDouble() ?: return null
    val lng = this["lng"].asDouble() ?: return null
    return SavedLocation(
        id = this["id"] as? String,
        name = (this["name"] as? String) ?: "",
        lat = lat,
        lng = lng,
        address = (this["address"] as? String) ?: "",
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toPredictionResult(): PredictionResult {
    val p10 = this["p10"].asDouble() ?: 0.0
    val p50 = this["p50"].asDouble() ?: 0.0
    val p90 = this["p90"].asDouble() ?: 0.0
    val unit = (this["unit"] as? String) ?: "Wh"
    val timestamp = this["timestamp"] as? String
    val dateFrom = this["date_from"] as? String
    val dateTo = this["date_to"] as? String

    val hourlyRaw = this["hourly"] as? List<Map<String, Any?>> ?: emptyList()
    val hourly = hourlyRaw.mapNotNull { h ->
        val ts = h["timestamp"] as? String ?: return@mapNotNull null
        HourlyPoint(
            timestamp = ts,
            p10 = h["p10"].asDouble() ?: 0.0,
            p50 = h["p50"].asDouble() ?: 0.0,
            p90 = h["p90"].asDouble() ?: 0.0,
            ghi = h["GHI"].asDouble() ?: 0.0,
            temp = h["temp"].asDouble() ?: 0.0,
            cloudsAll = h["clouds_all"].asInt() ?: 0,
        )
    }

    val dailyRaw = this["daily_totals"] as? List<Map<String, Any?>> ?: emptyList()
    val dailyTotals = dailyRaw.mapNotNull { d ->
        val date = d["date"] as? String ?: return@mapNotNull null
        DailyTotal(
            date = date,
            p10TotalWh = d["p10_total_wh"].asDouble() ?: 0.0,
            p50TotalWh = d["p50_total_wh"].asDouble() ?: 0.0,
            p90TotalWh = d["p90_total_wh"].asDouble() ?: 0.0,
        )
    }

    val weatherMap = this["weather"] as? Map<String, Any?>
    val weather = weatherMap?.let {
        Weather(
            ghi = it["GHI"].asDouble(),
            temp = it["temp"].asDouble(),
            cloudsAll = it["clouds_all"].asInt(),
        )
    }

    // For range mode: aggregate totals from daily_totals (sum across all days)
    val (rp10, rp50, rp90) = if (dailyTotals.isNotEmpty()) {
        Triple(
            dailyTotals.sumOf { it.p10TotalWh },
            dailyTotals.sumOf { it.p50TotalWh },
            dailyTotals.sumOf { it.p90TotalWh },
        )
    } else {
        Triple(p10, p50, p90)
    }

    return PredictionResult(
        p10 = rp10,
        p50 = rp50,
        p90 = rp90,
        unit = unit,
        timestamp = timestamp,
        dateFrom = dateFrom,
        dateTo = dateTo,
        hourly = hourly,
        dailyTotals = dailyTotals,
        weather = weather,
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toPredictionRecord(): PredictionRecord? {
    val id = (this["doc_id"] ?: this["id"]) as? String ?: return null

    val result = this["result"] as? Map<String, Any?>
    // Default missing quantiles to 0.0 so a malformed legacy record doesn't
    // silently disappear from history. The UI shows 0 Wh for the affected
    // row, which signals "saved but data incomplete" instead of vanishing.
    val p10 = result?.get("p10").asDouble() ?: 0.0
    val p50 = result?.get("p50").asDouble() ?: 0.0
    val p90 = result?.get("p90").asDouble() ?: 0.0
    val unit = (result?.get("unit") as? String) ?: "Wh"

    val location = this["location"] as? Map<String, Any?>
    val lat = location?.get("lat").asDouble()
    val lng = location?.get("lng").asDouble()
    val locName = (location?.get("name") as? String).orEmpty()
    val locAddr = (location?.get("address") as? String).orEmpty()

    val panel = this["panel"] as? Map<String, Any?>
    val panelName = (panel?.get("name") as? String).orEmpty()
    val panelArea = panel?.get("area_m2").asDouble()
    val panelEff = panel?.get("efficiency").asDouble()

    val weather = this["weather_snapshot"] as? Map<String, Any?>
    val cloudCover = weather?.get("clouds_all").asInt()
    val rain = weather?.get("rain_1h").asDouble()
    val snow = weather?.get("snow_1h").asDouble()

    val ts = this["timestamp"]
    val timestampMillis = ts.asEpochMillis() ?: System.currentTimeMillis()

    return PredictionRecord(
        id = id,
        timestampMillis = timestampMillis,
        p10 = p10,
        p50 = p50,
        p90 = p90,
        unit = unit,
        locationLat = lat,
        locationLng = lng,
        locationName = locName,
        locationAddress = locAddr,
        panelName = panelName,
        panelAreaM2 = panelArea,
        panelEfficiency = panelEff,
        cloudCover = cloudCover,
        rainMm = rain,
        snowMm = snow,
        isFavorite = (this["is_favorite"] as? Boolean) ?: false,
        dateFrom = this["date_from"] as? String,
        dateTo = this["date_to"] as? String,
    )
}

private fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}

private fun Any?.asInt(): Int? = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull()
    else -> null
}

private fun Any?.asEpochMillis(): Long? = when (this) {
    is Number -> toLong()
    is String -> runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(this).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    is Map<*, *> -> {
        val seconds = (this["_seconds"] ?: this["seconds"]).asDouble()
        seconds?.let { (it * 1000L).toLong() }
    }
    else -> null
}
