package com.example.solarpredict.nav

object Routes {
    // --- Auth ---
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"

    // --- Bottom-tab destinations ---
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    // --- Settings sub-screens ---
    const val ACCOUNT_INFO = "account_info"
    const val SAVED_PANELS = "saved_panels"
    const val EDIT_PANEL = "edit_panel"
    const val EDIT_PANEL_PATTERN = "edit_panel?id={id}"
    fun editPanel(id: String? = null) =
        if (id == null) "edit_panel?id=" else "edit_panel?id=$id"

    const val SAVED_LOCATIONS = "saved_locations"
    const val ADD_LOCATION = "add_location"
    // Edit reuses AddLocationScreen with an `editingId`; using a different
    // pattern keeps NavHost matching simple and lets us preserve the add
    // route untouched.
    const val EDIT_LOCATION_PATTERN = "edit_location/{id}"
    fun editLocation(id: String) = "edit_location/$id"

    // --- Wizard (full-screen, no bottom bar) ---
    const val LOCATION = "wizard_location"
    const val PANEL_CONFIG = "wizard_panel_config"
    const val RESULT = "wizard_result"

    // --- Prediction detail (read-only view of a saved prediction) ---
    const val PREDICTION_DETAIL_PATTERN = "prediction_detail/{id}"
    fun predictionDetail(id: String) = "prediction_detail/$id"
}
