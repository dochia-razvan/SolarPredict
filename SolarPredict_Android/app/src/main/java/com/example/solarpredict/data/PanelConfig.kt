package com.example.solarpredict.data

/**
 * One panel configuration. The UI uses [efficiencyPercent] (0..100) since
 * users type percentages; conversion to decimal happens at the API boundary.
 *
 * Capacity (kWp) and orientation/tilt removed - the trained ML models do
 * not use them, so they only added noise to the UI and Firestore.
 */
data class PanelConfig(
    val id: String? = null,
    val name: String,
    val areaM2: Double,
    val efficiencyPercent: Double,
)
