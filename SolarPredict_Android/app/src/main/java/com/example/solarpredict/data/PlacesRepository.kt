package com.example.solarpredict.data

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.tasks.await

/**
 * One autocomplete suggestion shown in the search dropdown.
 *
 * `placeId` is what we hand back to [PlacesRepository.fetchPlaceDetails] to
 * resolve the actual lat/lng + formatted address.
 */
data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)

/** Resolved place: lat/lng + a human-readable address suitable for SavedLocation. */
data class PlaceDetails(
    val latLng: LatLng,
    val name: String,
    val address: String,
)

/**
 * Wrapper around the Places SDK so the UI never deals with raw SDK types.
 *
 * Why a session token: Places billing is cheaper when an autocomplete
 * session (keystrokes -> pick result -> fetchPlace) shares one
 * [AutocompleteSessionToken]. Callers should call [newSessionToken] once
 * per "search interaction" and reuse it for every prediction request and
 * the final fetchPlaceDetails.
 */
class PlacesRepository(context: Context) {
    private val client = Places.createClient(context.applicationContext)

    fun newSessionToken(): AutocompleteSessionToken =
        AutocompleteSessionToken.newInstance()

    /**
     * Fetch up to ~5 autocomplete predictions for the given query. Returns
     * an empty list when the SDK is not initialised or the query is blank.
     */
    suspend fun autocomplete(
        query: String,
        token: AutocompleteSessionToken,
    ): List<PlaceSuggestion> {
        if (!Places.isInitialized() || query.isBlank()) return emptyList()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .build()
        return runCatching {
            val response = client.findAutocompletePredictions(request).await()
            response.autocompletePredictions.map {
                PlaceSuggestion(
                    placeId = it.placeId,
                    primaryText = it.getPrimaryText(null).toString(),
                    secondaryText = it.getSecondaryText(null).toString(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetch lat/lng + formatted address for a given placeId. Throws on
     * failure so the caller can surface a user-visible error.
     */
    suspend fun fetchPlaceDetails(
        placeId: String,
        token: AutocompleteSessionToken,
    ): PlaceDetails {
        val fields = listOf(
            Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG,
        )
        val request = FetchPlaceRequest.builder(placeId, fields)
            .setSessionToken(token)
            .build()
        val response = client.fetchPlace(request).await()
        val place = response.place
        val ll = place.latLng ?: error("Place has no coordinates")
        return PlaceDetails(
            latLng = ll,
            name = place.name.orEmpty(),
            address = place.address.orEmpty(),
        )
    }
}
