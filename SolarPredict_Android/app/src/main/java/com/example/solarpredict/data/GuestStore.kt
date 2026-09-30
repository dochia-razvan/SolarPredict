package com.example.solarpredict.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-lifetime in-memory storage for anonymous Firebase users.
 *
 * Per spec, guest data must NOT be written to Firestore - it is held only
 * in memory and is lost when the app process dies. The repositories below
 * route reads/writes here whenever [isGuest] is true.
 */
object GuestStore {
    val panels = MutableStateFlow<List<PanelConfig>>(emptyList())
    val locations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val predictions = MutableStateFlow<List<PredictionRecord>>(emptyList())

    fun reset() {
        panels.value = emptyList()
        locations.value = emptyList()
        predictions.value = emptyList()
    }

    fun addPanel(panel: PanelConfig): PanelConfig {
        val newId = panel.id ?: "guest-panel-${System.currentTimeMillis()}-${panels.value.size}"
        val saved = panel.copy(id = newId)
        panels.value = panels.value + saved
        return saved
    }

    fun updatePanel(panel: PanelConfig): Boolean {
        val list = panels.value.toMutableList()
        val idx = list.indexOfFirst { it.id == panel.id }
        if (idx < 0) return false
        list[idx] = panel
        panels.value = list
        return true
    }

    fun removePanel(id: String) {
        panels.value = panels.value.filterNot { it.id == id }
    }

    fun addLocation(loc: SavedLocation): SavedLocation {
        val newId = loc.id ?: "guest-loc-${System.currentTimeMillis()}-${locations.value.size}"
        val saved = loc.copy(id = newId)
        locations.value = locations.value + saved
        return saved
    }

    fun updateLocation(loc: SavedLocation): Boolean {
        val list = locations.value.toMutableList()
        val idx = list.indexOfFirst { it.id == loc.id }
        if (idx < 0) return false
        list[idx] = loc
        locations.value = list
        return true
    }

    fun removeLocation(id: String) {
        locations.value = locations.value.filterNot { it.id == id }
    }

    fun addPrediction(record: PredictionRecord): PredictionRecord {
        val saved = if (record.id.isBlank()) {
            record.copy(id = "guest-pred-${System.currentTimeMillis()}-${predictions.value.size}")
        } else record
        predictions.value = listOf(saved) + predictions.value
        return saved
    }

    fun deletePrediction(id: String) {
        predictions.value = predictions.value.filterNot { it.id == id }
    }

    fun toggleFavorite(id: String, value: Boolean) {
        predictions.value = predictions.value.map {
            if (it.id == id) it.copy(isFavorite = value) else it
        }
    }
}

/**
 * True if the currently signed-in Firebase user is anonymous (guest mode).
 *
 * Note: this reads `Firebase.auth.currentUser` synchronously. Firebase SDK
 * restores the cached user during `initializeApp()` in `onCreate()`, so by
 * the time any composable/ViewModel calls this it returns the right value.
 * On the very first launch (before the user has signed in once), this
 * returns false because `currentUser` is null, which is also correct since
 * an unauthenticated user is not a "guest" — they're not signed in at all.
 */
fun isGuest(): Boolean = Firebase.auth.currentUser?.isAnonymous == true
