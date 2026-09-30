package com.example.solarpredict.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SolarDarkColorScheme = darkColorScheme(
    primary = SolarOrange,
    onPrimary = SolarOnPrimary,
    secondary = SolarGold,
    onSecondary = SolarOnSecondary,
    background = SolarBackground,
    onBackground = SolarOnBackground,
    surface = SolarSurface,
    onSurface = SolarOnSurface,
    surfaceVariant = SolarSurfaceVariant,
    onSurfaceVariant = SolarOnSurfaceVariant,
    outline = SolarOutline,
    error = SolarError,
)

@Composable
fun SolarPredictTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SolarDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // Light icons on dark status bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
