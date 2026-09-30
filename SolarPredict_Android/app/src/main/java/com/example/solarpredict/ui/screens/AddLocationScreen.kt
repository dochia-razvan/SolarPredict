package com.example.solarpredict.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.solarpredict.data.SavedLocation
import com.example.solarpredict.localization.LocalAppStrings
import com.example.solarpredict.ui.components.PlacesAutocompleteField
import com.example.solarpredict.viewmodel.LocationsListViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

private val DEFAULT_LATLNG = LatLng(44.4268, 26.1025)

/**
 * Add OR edit a saved location.
 *
 * When [editingId] is null we insert a new doc; when non-null we load that
 * doc's current values into the form and call [LocationsListViewModel.update]
 * on save instead of [add].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    editingId: String? = null,
    viewModel: LocationsListViewModel = viewModel(factory = LocationsListViewModel.Factory),
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.uiState.collectAsState()

    // Reliable keyboard dismiss: clearFocus alone is occasionally a no-op,
    // calling the keyboard controller's hide() guarantees the IME goes away.
    val dismissKeyboard: () -> Unit = remember(focusManager, keyboardController) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    val locationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_LATLNG, 12f)
    }
    val markerState = rememberMarkerState(position = DEFAULT_LATLNG)

    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf(DEFAULT_LATLNG.latitude) }
    var lng by rememberSaveable { mutableStateOf(DEFAULT_LATLNG.longitude) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var prefilled by rememberSaveable(editingId) { mutableStateOf(false) }

    // When editing, prefill the form from the list once it loads.
    // Guard with `prefilled` so user edits aren't reverted on each refresh.
    LaunchedEffect(editingId, state.items) {
        if (editingId == null || prefilled) return@LaunchedEffect
        state.items.firstOrNull { it.id == editingId }?.let { existing ->
            name = existing.name
            address = existing.address
            lat = existing.lat
            lng = existing.lng
            markerState.position = LatLng(existing.lat, existing.lng)
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(existing.lat, existing.lng), 14f,
            )
            prefilled = true
        }
    }

    fun moveTo(latLng: LatLng) {
        lat = latLng.latitude
        lng = latLng.longitude
        markerState.position = latLng
        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 14f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { fetchCurrentLocation(locationClient)?.let(::moveTo) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editingId == null) s.addLocation else s.editLocation,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s.back,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        // Outer Box: imePadding shifts content above the keyboard so the Save
        // button stays reachable, and detectTapGestures dismisses the keyboard
        // for any tap on empty space (taps consumed by children — buttons,
        // text fields, the map — handle dismissal in their own onClicks).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { dismissKeyboard() })
                },
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // --- Search bar with Places autocomplete ---
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                PlacesAutocompleteField(
                    query = query,
                    onQueryChange = {
                        query = it
                        error = null
                    },
                    onPlacePicked = { details ->
                        moveTo(details.latLng)
                        if (name.isBlank()) name = details.name
                        address = details.address
                        query = details.name.ifBlank { details.address }
                        dismissKeyboard()
                    },
                )
            }

            // --- Map ---
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = MapType.NORMAL),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = false,
                        mapToolbarEnabled = false,
                    ),
                    onMapClick = { latLng ->
                        dismissKeyboard()
                        moveTo(latLng)
                    },
                ) {
                    Marker(
                        state = markerState,
                        draggable = true,
                        icon = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_ORANGE
                        ),
                    )
                }
            }

            // --- Bottom panel: name + lat/lng + save (extends to bottom edge) ---
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text(s.locationName + " *") },
                        singleLine = true,
                        colors = addLocFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "%.5f".format(Locale.US, lat),
                            onValueChange = { v ->
                                v.toDoubleOrNull()?.let { newLat ->
                                    if (newLat in -90.0..90.0) {
                                        // Move the marker too — typing
                                        // coordinates should keep the map
                                        // and pin in sync.
                                        moveTo(LatLng(newLat, lng))
                                    } else {
                                        lat = newLat
                                    }
                                }
                            },
                            label = { Text(s.latitude) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = addLocFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = "%.5f".format(Locale.US, lng),
                            onValueChange = { v ->
                                v.toDoubleOrNull()?.let { newLng ->
                                    if (newLng in -180.0..180.0) {
                                        moveTo(LatLng(lat, newLng))
                                    } else {
                                        lng = newLng
                                    }
                                }
                            },
                            label = { Text(s.longitude) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = addLocFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            dismissKeyboard()
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                scope.launch {
                                    fetchCurrentLocation(locationClient)?.let(::moveTo)
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text(s.useMyCurrentLocation,
                            color = MaterialTheme.colorScheme.onSurface)
                    }

                    error?.let {
                        Text(it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            dismissKeyboard()
                            val cleanName = name.trim()
                            // When editing, allow keeping the same name as
                            // the location being edited; only flag clashes
                            // against *other* locations.
                            val nameClash = state.items.any {
                                it.name.equals(cleanName, ignoreCase = true)
                                    && it.id != editingId
                            }
                            when {
                                cleanName.isBlank() -> {
                                    error = s.nameRequired; return@Button
                                }
                                nameClash -> {
                                    error = s.locationNameExists; return@Button
                                }
                                lat < -90.0 || lat > 90.0 -> {
                                    error = s.coordsLatRange; return@Button
                                }
                                lng < -180.0 || lng > 180.0 -> {
                                    error = s.coordsLngRange; return@Button
                                }
                            }
                            saving = true
                            val payload = SavedLocation(
                                id = editingId,
                                name = cleanName,
                                lat = lat,
                                lng = lng,
                                address = address.trim(),
                            )
                            val onResult: (Boolean, String?) -> Unit = { ok, msg ->
                                saving = false
                                if (ok) onSaved() else error = msg
                            }
                            if (editingId == null) {
                                viewModel.add(payload, onResult)
                            } else {
                                viewModel.update(payload, onResult)
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 8.dp,
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text(
                            if (editingId == null) s.save else s.saveChanges,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun fetchCurrentLocation(
    client: com.google.android.gms.location.FusedLocationProviderClient,
): LatLng? = try {
    client.lastLocation.await()?.let { LatLng(it.latitude, it.longitude) }
} catch (_: SecurityException) { null }

@Composable
private fun addLocFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)
