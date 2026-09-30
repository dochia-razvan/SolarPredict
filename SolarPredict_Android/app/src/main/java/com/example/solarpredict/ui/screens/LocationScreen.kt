package com.example.solarpredict.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.solarpredict.viewmodel.LocationScreenViewModel
import com.example.solarpredict.viewmodel.WizardViewModel
import com.google.android.gms.location.FusedLocationProviderClient
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
import kotlin.math.abs

private val DEFAULT_LAT_LNG = LatLng(44.4268, 26.1025)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    wizardVM: WizardViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: LocationScreenViewModel = viewModel(factory = LocationScreenViewModel.Factory),
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.uiState.collectAsState()

    // Single helper to fully dismiss the soft keyboard.
    // `focusManager.clearFocus()` alone is sometimes a no-op on the IME
    // (the focus owner thinks it lost focus but the IME stays up). Calling
    // both `keyboardController.hide()` AND `clearFocus()` is the only
    // pattern that works reliably across devices/keyboards.
    val dismissKeyboard: () -> Unit = remember(focusManager, keyboardController) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    val locationClient: FusedLocationProviderClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // --- Map state ---
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_LAT_LNG, 12f)
    }
    val markerState = rememberMarkerState(position = DEFAULT_LAT_LNG)

    // --- Editable text state for lat/lng ---
    var latText by rememberSaveable { mutableStateOf("%.5f".format(Locale.US, DEFAULT_LAT_LNG.latitude)) }
    var lngText by rememberSaveable { mutableStateOf("%.5f".format(Locale.US, DEFAULT_LAT_LNG.longitude)) }
    var coordError by remember { mutableStateOf<String?>(null) }

    // --- Search query (driven by Places autocomplete) ---
    var searchText by rememberSaveable { mutableStateOf("") }
    var resolvedAddress by rememberSaveable { mutableStateOf("") }

    // --- Save toggle + name ---
    var saveToggle by rememberSaveable { mutableStateOf(false) }
    var saveName by rememberSaveable { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    // --- Saved-location dropdown ---
    var dropdownOpen by remember { mutableStateOf(false) }

    // --- Permission launcher (current location) ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                fetchLastLocation(locationClient)?.let { latLng ->
                    moveTo(latLng, markerState, cameraPositionState)
                    latText = "%.5f".format(Locale.US, latLng.latitude)
                    lngText = "%.5f".format(Locale.US, latLng.longitude)
                }
            }
        }
    }

    // Sync map marker -> text fields when user taps the map
    LaunchedEffect(markerState.position) {
        latText = "%.5f".format(Locale.US, markerState.position.latitude)
        lngText = "%.5f".format(Locale.US, markerState.position.longitude)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        s.stepOneOfTwo,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
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
        // Tap-anywhere-to-dismiss + IME padding.
        //
        // - `imePadding()` shifts the layout up when the keyboard appears so
        //   the Next button (and the rest of the bottom panel) stays
        //   reachable. Without this the keyboard physically covers the
        //   button — that's what made it look "unclickable".
        // - `detectTapGestures` only fires for taps NOT consumed by a child
        //   (so tapping the search bar still focuses it). For everything
        //   else — empty space, the bottom panel padding, etc. — we
        //   dismiss the keyboard.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { dismissKeyboard() })
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- "Use Saved Location" picker (rendered ABOVE the search
                //      bar, expands inline so the list visually drops out of
                //      the button it was triggered from). Only rendered when
                //      the user has at least one saved location.
                if (state.savedLocations.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        SavedLocationsExpander(
                            savedLocations = state.savedLocations,
                            expanded = dropdownOpen,
                            onToggle = {
                                dismissKeyboard()
                                dropdownOpen = !dropdownOpen
                            },
                            onPick = { loc ->
                                val latLng = LatLng(loc.lat, loc.lng)
                                moveTo(latLng, markerState, cameraPositionState)
                                latText = "%.5f".format(Locale.US, loc.lat)
                                lngText = "%.5f".format(Locale.US, loc.lng)
                                saveName = loc.name
                                resolvedAddress = loc.address
                                // Intentionally do NOT set `searchText` to the
                                // saved location name — the search bar is for
                                // *addresses*, not labels like "Home".
                                dropdownOpen = false
                                // And do NOT focus the search bar afterwards.
                                dismissKeyboard()
                            },
                        )
                    }
                }

                // --- Search bar (Places Autocomplete) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    PlacesAutocompleteField(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        onPlacePicked = { details ->
                            val ll = details.latLng
                            moveTo(ll, markerState, cameraPositionState)
                            latText = "%.5f".format(Locale.US, ll.latitude)
                            lngText = "%.5f".format(Locale.US, ll.longitude)
                            resolvedAddress = details.address
                            searchText = details.name.ifBlank { details.address }
                            dismissKeyboard()
                        },
                        placeholder = s.searchAddress,
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
                            // Tapping the map dismisses the keyboard too.
                            dismissKeyboard()
                            markerState.position = latLng
                            coordError = null
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

                // --- Bottom panel: rounded TOP corners only, the rest fills the
                //     bottom of the screen so no background bleeds through.
                //
                //     Trick: we paint the surface color on the Box behind the
                //     rounded sub-surface so the bottom (below the rounded top)
                //     is also covered solidly.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latText,
                                onValueChange = { latText = it; coordError = null },
                                label = { Text(s.latitude) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = coordError != null,
                                colors = mapFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = lngText,
                                onValueChange = { lngText = it; coordError = null },
                                label = { Text(s.longitude) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = coordError != null,
                                colors = mapFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        coordError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        OutlinedButton(
                            onClick = {
                                dismissKeyboard()
                                requestLocationOrFetch(
                                    context = context,
                                    client = locationClient,
                                    coroutineScope = scope,
                                    onResolved = { latLng ->
                                        moveTo(latLng, markerState, cameraPositionState)
                                        latText = "%.5f".format(Locale.US, latLng.latitude)
                                        lngText = "%.5f".format(Locale.US, latLng.longitude)
                                    },
                                    onNeedsPermission = {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    },
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(s.useMyCurrentLocation,
                                color = MaterialTheme.colorScheme.onSurface)
                        }

                        // --- Save toggle ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.saveLocationOption,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(s.saveLocationHelper,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = saveToggle,
                                onCheckedChange = {
                                    dismissKeyboard()
                                    saveToggle = it
                                    saveError = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                        if (saveToggle) {
                            OutlinedTextField(
                                value = saveName,
                                onValueChange = { saveName = it; saveError = null },
                                label = { Text(s.locationName + " *") },
                                singleLine = true,
                                isError = saveError != null,
                                supportingText = {
                                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                },
                                colors = mapFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Button(
                            onClick = {
                                // Always drop the keyboard first so the user
                                // sees the next screen / any validation
                                // errors without the IME in the way.
                                dismissKeyboard()
                                val latNum = latText.toDoubleOrNull()
                                val lngNum = lngText.toDoubleOrNull()
                                when {
                                    latNum == null || latNum < -90.0 || latNum > 90.0 -> {
                                        coordError = s.coordsLatRange
                                        return@Button
                                    }
                                    lngNum == null || lngNum < -180.0 || lngNum > 180.0 -> {
                                        coordError = s.coordsLngRange
                                        return@Button
                                    }
                                }
                                val cleanName = saveName.trim()
                                if (saveToggle) {
                                    if (cleanName.isBlank()) {
                                        saveError = s.nameRequired
                                        return@Button
                                    }
                                    if (state.savedLocations.any {
                                            it.name.equals(cleanName, ignoreCase = true)
                                        }) {
                                        saveError = s.locationNameExists
                                        return@Button
                                    }
                                }
                                val resolvedName = when {
                                    cleanName.isNotBlank() -> cleanName
                                    resolvedAddress.isNotBlank() -> resolvedAddress
                                    else -> formatCoords(latNum!!, lngNum!!)
                                }
                                val savedLocation = SavedLocation(
                                    name = resolvedName,
                                    lat = latNum!!,
                                    lng = lngNum!!,
                                    address = resolvedAddress,
                                )
                                wizardVM.setLocation(savedLocation)
                                if (saveToggle) {
                                    viewModel.saveLocation(savedLocation) { _, _ -> }
                                }
                                onNext()
                            },
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
                            Text(s.next, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Use Saved Location" picker that expands inline.
 *
 * Replaces the floating [androidx.compose.material3.DropdownMenu] popup with a
 * single Card whose body is the button row + an animated list of options. The
 * list is visually attached to the button (same Card, no gap) so the user can
 * see exactly what they tapped, and we get a much bigger touch target than
 * the default popup menu — important on phones with small screens.
 */
@Composable
private fun SavedLocationsExpander(
    savedLocations: List<SavedLocation>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (SavedLocation) -> Unit,
) {
    val s = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = onToggle,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        s.useSavedLocation,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                    savedLocations.forEachIndexed { i, loc ->
                        Surface(
                            onClick = { onPick(loc) },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        loc.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val secondary = buildString {
                                        append("%.4f, %.4f".format(Locale.US, loc.lat, loc.lng))
                                        if (loc.address.isNotBlank()) {
                                            append(" · ")
                                            append(loc.address)
                                        }
                                    }
                                    Text(
                                        secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (i != savedLocations.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                modifier = Modifier.padding(start = 44.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun moveTo(
    latLng: LatLng,
    markerState: com.google.maps.android.compose.MarkerState,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
) {
    markerState.position = latLng
    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 14f)
}

@SuppressLint("MissingPermission")
private fun requestLocationOrFetch(
    context: android.content.Context,
    client: FusedLocationProviderClient,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onResolved: (LatLng) -> Unit,
    onNeedsPermission: () -> Unit,
) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    if (!granted) { onNeedsPermission(); return }
    coroutineScope.launch { fetchLastLocation(client)?.let(onResolved) }
}

@SuppressLint("MissingPermission")
private suspend fun fetchLastLocation(client: FusedLocationProviderClient): LatLng? = try {
    client.lastLocation.await()?.let { LatLng(it.latitude, it.longitude) }
} catch (_: SecurityException) { null }

private fun formatCoords(lat: Double, lng: Double): String {
    val ns = if (lat >= 0) "N" else "S"
    val ew = if (lng >= 0) "E" else "W"
    return "%.2f°%s, %.2f°%s".format(abs(lat), ns, abs(lng), ew)
}

@Composable
private fun mapFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)
