package com.example.solarpredict.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.solarpredict.data.PlaceDetails
import com.example.solarpredict.data.PlaceSuggestion
import com.example.solarpredict.data.PlacesRepository
import com.example.solarpredict.localization.LocalAppStrings
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search field with Google Places Autocomplete:
 *  - Debounces the user input (240 ms) so we don't fire one Places call per keystroke
 *  - Reuses one [AutocompleteSessionToken] for the whole interaction (cheaper billing)
 *  - Renders the predictions as a styled dropdown directly under the field
 *  - Calls [onPlacePicked] with the resolved lat/lng + address when the user taps a row
 *
 * Drop this in any screen that needs address search — used by both the wizard
 * Location screen and the Saved Locations -> Add Location screen.
 */
@Composable
fun PlacesAutocompleteField(
    query: String,
    onQueryChange: (String) -> Unit,
    onPlacePicked: (PlaceDetails) -> Unit,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PlacesRepository(context) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var sessionToken by remember { mutableStateOf(repo.newSessionToken()) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Trigger autocomplete with 240 ms debounce so quick typing doesn't burn quota.
    LaunchedEffect(query) {
        debounceJob?.cancel()
        if (query.isBlank()) {
            suggestions = emptyList()
            loading = false
            return@LaunchedEffect
        }
        debounceJob = scope.launch {
            delay(240)
            loading = true
            val results = repo.autocomplete(query.trim(), sessionToken)
            suggestions = results
            loading = false
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(placeholder ?: s.searchPlace) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                when {
                    resolving || loading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = s.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Tapping the IME's "Search" action key dismisses the keyboard
            // (and clears focus) so the user has a one-tap way out without
            // having to reach for the screen.
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
            }),
            colors = autoCompleteFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    items(suggestions, key = { it.placeId }) { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            onTap = {
                                // Clear the dropdown *immediately* so the
                                // user doesn't see stale predictions while
                                // we fetch the place details.
                                suggestions = emptyList()
                                resolving = true
                                scope.launch {
                                    val details = runCatching {
                                        repo.fetchPlaceDetails(suggestion.placeId, sessionToken)
                                    }.getOrNull()
                                    resolving = false
                                    if (details != null) {
                                        onPlacePicked(details)
                                        // Each successful pick ends the autocomplete
                                        // session — start a fresh token so the next
                                        // search is its own billable session.
                                        sessionToken = repo.newSessionToken()
                                    }
                                }
                            },
                        )
                        if (suggestion != suggestions.last()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                modifier = Modifier.padding(start = 44.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: PlaceSuggestion, onTap: () -> Unit) {
    Surface(
        onClick = onTap,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    suggestion.primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (suggestion.secondaryText.isNotBlank()) {
                    Text(
                        suggestion.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun autoCompleteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)
