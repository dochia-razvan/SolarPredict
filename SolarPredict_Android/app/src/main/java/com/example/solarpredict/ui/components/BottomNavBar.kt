package com.example.solarpredict.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.solarpredict.localization.LocalAppStrings
import com.example.solarpredict.nav.Routes

private data class BottomTab(
    val route: String,
    val labelKey: TabLabel,
    val icon: ImageVector,
)

private enum class TabLabel { HOME, FAVORITES, HISTORY, SETTINGS }

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, TabLabel.HOME, Icons.Filled.Home),
    BottomTab(Routes.FAVORITES, TabLabel.FAVORITES, Icons.Filled.Star),
    BottomTab(Routes.HISTORY, TabLabel.HISTORY, Icons.Filled.Home),  // overwritten below
    BottomTab(Routes.SETTINGS, TabLabel.SETTINGS, Icons.Filled.Settings),
)

@Composable
fun SolarBottomNavBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val s = LocalAppStrings.current

    val tabs = listOf(
        BottomTab(Routes.HOME, TabLabel.HOME, Icons.Filled.Home),
        BottomTab(Routes.FAVORITES, TabLabel.FAVORITES, Icons.Filled.Star),
        BottomTab(Routes.HISTORY, TabLabel.HISTORY, Icons.Filled.History),
        BottomTab(Routes.SETTINGS, TabLabel.SETTINGS, Icons.Filled.Settings),
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val label = when (tab.labelKey) {
                TabLabel.HOME -> s.home
                TabLabel.FAVORITES -> s.favorites
                TabLabel.HISTORY -> s.history
                TabLabel.SETTINGS -> s.settings
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem
                    // We deliberately do NOT clear WizardSession when leaving
                    // Result via the bottom bar. Clearing synchronously
                    // before navigate() flips Result's location/panel to
                    // null and ResultScreen recomposes into its
                    // EmptyResultState branch for one frame — that's the
                    // brief screen flash users would see between Result
                    // and the next tab. The next "New Prediction" tap on
                    // Home already clears WizardSession, and the auth
                    // listener clears it on logout/account swap, so stale
                    // data lingering until then is harmless.
                    //
                    // We also deliberately do NOT use saveState/restoreState.
                    // Tabs here are short-lived list views; saving tab
                    // state across logout/login caused previous-account
                    // data to leak into a new session. Each tab re-fetches
                    // on resume, which is what we want anyway.
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

/**
 * Bottom bar shows on the four main tabs, the Result screen, and the read-only
 * Prediction Detail screen so the user can always jump to any tab.
 *
 * Note: `Routes.PREDICTION_DETAIL_PATTERN` is "prediction_detail/{id}" — the
 * literal pattern that NavController stores as the destination route, NOT
 * the resolved instance "prediction_detail/abc123". Matching by exact string
 * works because Compose Navigation reports the pattern, not the resolved URL.
 */
fun routeShowsBottomBar(route: String?): Boolean {
    if (route == null) return false
    return route == Routes.HOME ||
        route == Routes.FAVORITES ||
        route == Routes.HISTORY ||
        route == Routes.SETTINGS ||
        route == Routes.RESULT ||
        route == Routes.PREDICTION_DETAIL_PATTERN
}
