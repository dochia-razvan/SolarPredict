package com.example.solarpredict.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-lifetime holder for the *active* prediction session
 * (the location + panel chosen in the Location/Panel Config wizard).
 *
 * Why this exists: the Result screen needs the wizard inputs to render,
 * but it is *outside* the wizard nav graph (so the user cannot navigate
 * back into the wizard from Result via the device back button). Putting
 * the data here lets us pop the wizard graph the moment we navigate to
 * Result without losing the inputs.
 *
 * Lifetime contract:
 *   - Cleared on sign-out (see [AuthRepository.signOut]).
 *   - Cleared on every fresh wizard start (see Home → New Prediction).
 *   - Cleared when Result is replaced with another prediction.
 */
object WizardSession {
    val location = MutableStateFlow<SavedLocation?>(null)
    val panel = MutableStateFlow<PanelConfig?>(null)

    fun setLocation(loc: SavedLocation) { location.value = loc }
    fun setPanel(p: PanelConfig) { panel.value = p }

    fun clear() {
        location.value = null
        panel.value = null
    }
}
