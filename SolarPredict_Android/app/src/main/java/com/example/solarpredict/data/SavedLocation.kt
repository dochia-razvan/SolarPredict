package com.example.solarpredict.data

/** A user-saved map location. `id` is the Firestore doc id (null for guests/in-memory). */
data class SavedLocation(
    val id: String? = null,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String = "",
)
