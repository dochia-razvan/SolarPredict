package com.example.solarpredict.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.solarpredict.data.HourlyPoint
import com.example.solarpredict.data.PredictionResult
import com.example.solarpredict.data.SavedLocation
import com.example.solarpredict.localization.LocalAppStrings
import com.example.solarpredict.viewmodel.CustomRange
import com.example.solarpredict.viewmodel.ResultViewModel
import com.example.solarpredict.viewmodel.TimeRange
import com.example.solarpredict.viewmodel.WizardViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    wizardVM: WizardViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ResultViewModel = viewModel(factory = ResultViewModel.Factory),
) {
    val s = LocalAppStrings.current
    val state by viewModel.uiState.collectAsState()
    val location by wizardVM.location.collectAsState()
    val panel by wizardVM.panel.collectAsState()

    // Kick off prediction once when both wizard inputs are ready.
    LaunchedEffect(location, panel) {
        val l = location ?: return@LaunchedEffect
        val p = panel ?: return@LaunchedEffect
        if (state.prediction == null && !state.isLoadingPrediction) {
            viewModel.loadAndSave(l, p)
        }
    }

    var customDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        s.resultTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                actions = {
                    val canFavorite = state.savedDocId?.isNotBlank() == true
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        enabled = canFavorite && !state.isToggling,
                    ) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = s.markFavorite,
                            tint = if (state.isFavorite)
                                MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        // Defensive: if the wizard inputs are missing (e.g. the user navigated
        // here directly somehow, or state was wiped on logout), don't show a
        // forever-spinning loader. Show a clear empty state with a button
        // back to Home so the user is never trapped.
        if (location == null || panel == null) {
            EmptyResultState(
                onGoHome = onBack,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        if (state.isLoadingPrediction || state.prediction == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(s.goToHome)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            s.runningPrediction,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            return@Scaffold
        }

        ResultBody(
            state = state,
            location = location,
            onPickRange = { range ->
                if (range == TimeRange.CUSTOM) {
                    customDialogOpen = true
                } else {
                    val l = location ?: return@ResultBody
                    val p = panel ?: return@ResultBody
                    viewModel.loadAndSave(l, p, range = range, custom = null)
                }
            },
            onCustomTap = { customDialogOpen = true },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (customDialogOpen) {
        CustomRangeDialog(
            initial = state.customRange,
            onCancel = { customDialogOpen = false },
            onConfirm = { range ->
                customDialogOpen = false
                val l = location ?: return@CustomRangeDialog
                val p = panel ?: return@CustomRangeDialog
                viewModel.loadAndSave(l, p, range = TimeRange.CUSTOM, custom = range)
            },
        )
    }
}

@Composable
private fun EmptyResultState(
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            s.noActivePrediction,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onGoHome,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Filled.Home, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(s.goToHome, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResultBody(
    state: com.example.solarpredict.viewmodel.ResultUiState,
    location: SavedLocation?,
    onPickRange: (TimeRange) -> Unit,
    onCustomTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val pred = state.prediction!!
    val filtered = filterByRange(pred, state.timeRange, state.customRange)
    val buckets = remember(filtered, state.timeRange) {
        bucketize(filtered.hourly, state.timeRange)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Time range chips
        item {
            TimeRangeChips(
                selected = state.timeRange,
                customLabel = state.customRange?.let { formatCustomLabel(it) },
                onPick = onPickRange,
                onCustomTap = onCustomTap,
            )
        }

        // Hero card: P50 + range + chip
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            s.expectedEnergyProduction,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatKWh(filtered.totalP50Wh),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                s.confidenceInterval82,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        EstimateColumn(
                            label = s.lowestEstimate,
                            value = formatKWh(filtered.totalP10Wh),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EstimateColumn(
                            label = s.highestEstimate,
                            value = formatKWh(filtered.totalP90Wh),
                            color = MaterialTheme.colorScheme.secondary,
                            alignEnd = true,
                        )
                    }
                }
            }
        }

        // Chart card (with interactive tooltip)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        s.hourlyProduction,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.tapToSeeValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    InteractiveHourlyChart(
                        buckets = buckets,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }
        }

        // Location card
        if (location != null) {
            item {
                LocationCard(location)
            }
        }

        // Weather snapshot (current-conditions only, when present)
        pred.weather?.let { weather ->
            item { WeatherCard(weather) }
        }

        if (state.error != null) {
            item {
                Text(state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EstimateColumn(
    label: String,
    value: String,
    color: Color,
    alignEnd: Boolean = false,
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LocationCard(location: SavedLocation) {
    val s = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    s.location,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                location.name.ifBlank { "Selected location" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (location.address.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    location.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Lat: ${"%.5f".format(Locale.US, location.lat)} · Lng: ${"%.5f".format(Locale.US, location.lng)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeatherCard(weather: com.example.solarpredict.data.Weather) {
    val s = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WbSunny, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    s.weatherSnapshot,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                WeatherStat(
                    icon = Icons.Filled.WbSunny,
                    label = "GHI",
                    value = weather.ghi?.let { "${it.roundToInt()} W/m²" } ?: "—",
                )
                WeatherStat(
                    icon = Icons.Filled.Thermostat,
                    label = "Temp",
                    value = weather.temp?.let { "${"%.1f".format(it)}°C" } ?: "—",
                )
                WeatherStat(
                    icon = Icons.Filled.Cloud,
                    label = "Clouds",
                    value = weather.cloudsAll?.let { "$it%" } ?: "—",
                )
            }
        }
    }
}

@Composable
private fun WeatherStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimeRangeChips(
    selected: TimeRange,
    customLabel: String?,
    onPick: (TimeRange) -> Unit,
    onCustomTap: () -> Unit,
) {
    val s = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            TimeRange.NEXT_24H to s.next24h,
            TimeRange.NEXT_7D to s.next7d,
            TimeRange.NEXT_14D to s.next14d,
        ).forEach { (range, label) ->
            FilterChip(
                selected = selected == range,
                onClick = { onPick(range) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        FilterChip(
            selected = selected == TimeRange.CUSTOM,
            onClick = onCustomTap,
            label = { Text(customLabel ?: s.custom) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Interactive chart
// ---------------------------------------------------------------------------

@Composable
private fun InteractiveHourlyChart(
    buckets: List<ChartBucket>,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val orange = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val tooltipBg = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    if (buckets.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(s.noDataForRange, color = muted)
        }
        return
    }

    val maxKwh = max(0.001, buckets.maxOf { it.p90Kwh })

    // Hovered bucket index (null = no tooltip).
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    var canvasWidthPx by remember { mutableStateOf(0f) }
    var canvasHeightPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(buckets) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            hoverIndex = offsetToIndex(offset.x, buckets.size, size.width.toFloat())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            hoverIndex = offsetToIndex(change.position.x, buckets.size, size.width.toFloat())
                        },
                        onDragEnd = { hoverIndex = null },
                        onDragCancel = { hoverIndex = null },
                    )
                }.pointerInput(buckets) {
                    detectTapGestures(
                        onPress = { offset ->
                            hoverIndex = offsetToIndex(offset.x, buckets.size, size.width.toFloat())
                            try { awaitRelease() } finally { hoverIndex = null }
                        },
                    )
                },
            ) {
                canvasWidthPx = size.width
                canvasHeightPx = size.height
                val w = size.width
                val h = size.height
                val n = buckets.size
                if (n < 2) return@Canvas

                fun x(i: Int): Float = i * (w / (n - 1))
                fun y(v: Double): Float = (h - (v / maxKwh * h)).toFloat()

                // 4 horizontal grid lines
                val gridStrokePx = with(density) { 1.dp.toPx() }
                val dash = PathEffect.dashPathEffect(
                    floatArrayOf(with(density) { 4.dp.toPx() }, with(density) { 4.dp.toPx() }),
                )
                for (g in 1..4) {
                    val gy = h * g / 5f
                    drawLine(
                        color = outline,
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = gridStrokePx,
                        pathEffect = dash,
                    )
                }

                // P10-P90 envelope (filled area)
                val envelope = Path().apply {
                    moveTo(x(0), y(buckets[0].p90Kwh))
                    for (i in 1 until n) lineTo(x(i), y(buckets[i].p90Kwh))
                    for (i in n - 1 downTo 0) lineTo(x(i), y(buckets[i].p10Kwh))
                    close()
                }
                drawPath(envelope, color = orange.copy(alpha = 0.28f))

                // P50 line
                val p50Path = Path().apply {
                    moveTo(x(0), y(buckets[0].p50Kwh))
                    for (i in 1 until n) lineTo(x(i), y(buckets[i].p50Kwh))
                }
                drawPath(
                    path = p50Path,
                    color = gold,
                    style = Stroke(width = with(density) { 2.5.dp.toPx() }),
                )

                // Peak dot
                val peakIdx = buckets.withIndex().maxByOrNull { it.value.p50Kwh }?.index ?: 0
                drawCircle(
                    color = gold,
                    radius = with(density) { 4.dp.toPx() },
                    center = Offset(x(peakIdx), y(buckets[peakIdx].p50Kwh)),
                )

                // Crosshair + dot for the hovered point
                hoverIndex?.let { i ->
                    val cx = x(i)
                    val cy = y(buckets[i].p50Kwh)
                    drawLine(
                        color = onSurface.copy(alpha = 0.6f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, h),
                        strokeWidth = with(density) { 1.dp.toPx() },
                    )
                    drawCircle(
                        color = orange,
                        radius = with(density) { 6.dp.toPx() },
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = onSurface,
                        radius = with(density) { 3.dp.toPx() },
                        center = Offset(cx, cy),
                    )
                }
            }

            // Floating tooltip — placed at the hovered point with intrinsic size.
            hoverIndex?.let { i ->
                val bucket = buckets[i]
                val n = buckets.size
                if (n >= 2 && canvasWidthPx > 0f) {
                    val cx = i * (canvasWidthPx / (n - 1))
                    val tipWidthPx = with(density) { 130.dp.toPx() }
                    val padPx = with(density) { 8.dp.toPx() }
                    val xPx = (cx - tipWidthPx / 2)
                        .coerceIn(padPx, canvasWidthPx - tipWidthPx - padPx)
                    Surface(
                        color = tooltipBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .width(130.dp)
                            .offset { IntOffset(xPx.toInt(), 0) },
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                bucket.fullLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "%.2f kWh".format(Locale.US, bucket.p50Kwh),
                                style = MaterialTheme.typography.titleSmall,
                                color = orange,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "P10: %.2f · P90: %.2f".format(Locale.US, bucket.p10Kwh, bucket.p90Kwh),
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // X-axis labels — distinct per range type
        Row(modifier = Modifier.fillMaxWidth()) {
            val tickIndices = pickTickIndices(buckets.size)
            buckets.forEachIndexed { i, bucket ->
                if (i in tickIndices) {
                    Text(
                        bucket.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
                if (i != buckets.lastIndex) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun offsetToIndex(x: Float, count: Int, totalWidth: Float): Int {
    if (count <= 1 || totalWidth <= 0f) return 0
    val step = totalWidth / (count - 1)
    val i = (x / step).roundToInt()
    return i.coerceIn(0, count - 1)
}

private fun pickTickIndices(n: Int): Set<Int> {
    if (n <= 1) return setOf(0)
    if (n <= 6) return (0 until n).toSet()
    val target = 6
    val step = (n - 1).toFloat() / (target - 1)
    return (0 until target).map { (it * step).roundToInt() }.toSet()
}

// ---------------------------------------------------------------------------
// Custom range dialog
// ---------------------------------------------------------------------------

@Composable
private fun CustomRangeDialog(
    initial: CustomRange?,
    onCancel: () -> Unit,
    onConfirm: (CustomRange) -> Unit,
) {
    val s = LocalAppStrings.current
    val today = LocalDate.now()
    val maxDate = today.plusDays(13)

    var startDate by remember { mutableStateOf(initial?.start?.toLocalDate() ?: today) }
    var startHour by remember { mutableStateOf(initial?.start?.hour ?: 0) }
    var endDate by remember { mutableStateOf(initial?.end?.toLocalDate() ?: today.plusDays(1)) }
    var endHour by remember { mutableStateOf(initial?.end?.hour ?: 23) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(s.customRange) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.customRangeHelper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                DateTimePickerRow(
                    label = s.start,
                    date = startDate,
                    hour = startHour,
                    minDate = today,
                    maxDate = maxDate,
                    onPickDate = { d ->
                        startDate = d
                        if (endDate.isBefore(d)) endDate = d
                    },
                    onPickHour = { startHour = it },
                )
                DateTimePickerRow(
                    label = s.end,
                    date = endDate,
                    hour = endHour,
                    minDate = startDate,
                    maxDate = maxDate,
                    onPickDate = { endDate = it },
                    onPickHour = { endHour = it },
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = LocalDateTime.of(startDate, java.time.LocalTime.of(startHour, 0))
                val end = LocalDateTime.of(endDate, java.time.LocalTime.of(endHour, 0))
                val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
                when {
                    !end.isAfter(start) -> error = s.endMustBeAfterStart
                    days > 13 -> error = s.maxRangeDays
                    else -> onConfirm(CustomRange(start, end))
                }
            }) { Text(s.apply) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(s.cancel) }
        },
    )
}

@Composable
private fun DateTimePickerRow(
    label: String,
    date: LocalDate,
    hour: Int,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    onPickHour: (Int) -> Unit,
) {
    val context = LocalContext.current
    val datePattern = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            val dlg = DatePickerDialog(
                context,
                { _, y, m, d -> onPickDate(LocalDate.of(y, m + 1, d)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            )
            dlg.datePicker.minDate = minDate.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            dlg.datePicker.maxDate = maxDate.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            dlg.show()
        }) {
            Text(date.format(datePattern), color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = {
            TimePickerDialog(
                context,
                { _, h, _ -> onPickHour(h) },
                hour, 0, true,
            ).show()
        }) {
            Text("%02d:00".format(hour), color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ===========================================================================
// Aggregation + range filtering
// ===========================================================================

/**
 * One bucket on the X axis.
 *
 * The bucket granularity depends on the active range:
 *   - 24h:  hourly buckets, label = "00:00", "03:00", ...
 *   - 7d:   daily buckets,  label = "Mon", "Tue"...
 *   - 14d:  daily buckets,  label = "Apr 29", "Apr 30"...
 *   - custom: hourly if span <=2 days, otherwise daily.
 */
private data class ChartBucket(
    val shortLabel: String,
    val fullLabel: String,
    val p10Kwh: Double,
    val p50Kwh: Double,
    val p90Kwh: Double,
)

private fun bucketize(points: List<HourlyPoint>, range: TimeRange): List<ChartBucket> {
    if (points.isEmpty()) return emptyList()
    return when (range) {
        TimeRange.NEXT_24H -> hourlyBuckets(points, hourLabelFmt = "%02d:00")
        TimeRange.NEXT_7D -> dailyBuckets(points, useWeekday = true)
        TimeRange.NEXT_14D -> dailyBuckets(points, useWeekday = false)
        TimeRange.CUSTOM -> {
            // Decide based on actual span; <= 48 hours -> hourly, else daily.
            val firstTs = parseLocalIso(points.first().timestamp)
            val lastTs = parseLocalIso(points.last().timestamp)
            val hours = if (firstTs != null && lastTs != null) {
                java.time.Duration.between(firstTs, lastTs).toHours()
            } else 0L
            if (hours <= 48L) hourlyBuckets(points, hourLabelFmt = "%02d:00")
            else dailyBuckets(points, useWeekday = false)
        }
    }
}

private fun hourlyBuckets(points: List<HourlyPoint>, hourLabelFmt: String): List<ChartBucket> {
    // Group 15-min slots into hours by truncating the timestamp to "YYYY-MM-DDTHH".
    val buckets = linkedMapOf<String, DoubleArray>()
    for (p in points) {
        val key = p.timestamp.take(13).ifBlank { p.timestamp }
        val arr = buckets.getOrPut(key) { DoubleArray(3) }
        arr[0] += p.p10
        arr[1] += p.p50
        arr[2] += p.p90
    }
    val fullFmt = DateTimeFormatter.ofPattern("MMM d, HH:00", Locale.ENGLISH)
    return buckets.map { (key, arr) ->
        val ts = parseLocalIso(key + ":00:00")
        val hour = ts?.hour ?: 0
        ChartBucket(
            shortLabel = hourLabelFmt.format(hour),
            fullLabel = ts?.format(fullFmt) ?: key,
            p10Kwh = arr[0] / 1000.0,
            p50Kwh = arr[1] / 1000.0,
            p90Kwh = arr[2] / 1000.0,
        )
    }
}

private fun dailyBuckets(points: List<HourlyPoint>, useWeekday: Boolean): List<ChartBucket> {
    val buckets = linkedMapOf<String, DoubleArray>()
    for (p in points) {
        val key = p.timestamp.take(10) // YYYY-MM-DD
        val arr = buckets.getOrPut(key) { DoubleArray(3) }
        arr[0] += p.p10
        arr[1] += p.p50
        arr[2] += p.p90
    }
    val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val fullFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
    return buckets.map { (key, arr) ->
        val date = runCatching { LocalDate.parse(key) }.getOrNull()
        val short = date?.format(if (useWeekday) weekdayFmt else dateFmt) ?: key
        val full = date?.format(fullFmt) ?: key
        ChartBucket(
            shortLabel = short,
            fullLabel = full,
            p10Kwh = arr[0] / 1000.0,
            p50Kwh = arr[1] / 1000.0,
            p90Kwh = arr[2] / 1000.0,
        )
    }
}

private data class FilteredResult(
    val hourly: List<HourlyPoint>,
    val totalP10Wh: Double,
    val totalP50Wh: Double,
    val totalP90Wh: Double,
)

private fun filterByRange(
    pred: PredictionResult,
    range: TimeRange,
    custom: CustomRange?,
): FilteredResult {
    val now = LocalDateTime.now()
    val start: LocalDateTime
    val end: LocalDateTime
    when (range) {
        TimeRange.NEXT_24H -> { start = now; end = now.plusHours(24) }
        TimeRange.NEXT_7D -> { start = now; end = now.plusDays(7) }
        TimeRange.NEXT_14D -> { start = now; end = now.plusDays(14) }
        TimeRange.CUSTOM -> {
            if (custom == null) {
                start = now; end = now.plusHours(24)
            } else {
                start = custom.start; end = custom.end
            }
        }
    }
    val filtered = pred.hourly.filter {
        val t = parseLocalIso(it.timestamp) ?: return@filter false
        !t.isBefore(start) && !t.isAfter(end)
    }
    return FilteredResult(
        hourly = filtered,
        totalP10Wh = filtered.sumOf { it.p10 },
        totalP50Wh = filtered.sumOf { it.p50 },
        totalP90Wh = filtered.sumOf { it.p90 },
    )
}

private fun parseLocalIso(s: String): LocalDateTime? = runCatching {
    LocalDateTime.parse(s)
}.getOrNull()

private fun formatKWh(wh: Double): String {
    val kwh = wh / 1000.0
    return "%.2f kWh".format(Locale.US, kwh)
}

private fun formatCustomLabel(c: CustomRange): String {
    val pat = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH)
    return "${c.start.format(pat)} – ${c.end.format(pat)}"
}
