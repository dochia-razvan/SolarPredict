package com.example.solarpredict.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.solarpredict.data.LANG_EN
import com.example.solarpredict.data.SettingsPreferences
import com.example.solarpredict.localization.LocalAppStrings
import com.example.solarpredict.localization.appStringsFor
import com.example.solarpredict.ui.components.SolarBottomNavBar
import com.example.solarpredict.ui.components.routeShowsBottomBar
import com.example.solarpredict.ui.screens.AccountInfoScreen
import com.example.solarpredict.ui.screens.AddLocationScreen
import com.example.solarpredict.ui.screens.EditPanelScreen
import com.example.solarpredict.ui.screens.FavoritesScreen
import com.example.solarpredict.ui.screens.HistoryScreen
import com.example.solarpredict.ui.screens.HomeScreen
import com.example.solarpredict.ui.screens.LocationScreen
import com.example.solarpredict.ui.screens.LoginScreen
import com.example.solarpredict.ui.screens.PanelConfigScreen
import com.example.solarpredict.ui.screens.PredictionDetailScreen
import com.example.solarpredict.ui.screens.ResultScreen
import com.example.solarpredict.ui.screens.SavedLocationsScreen
import com.example.solarpredict.ui.screens.SavedPanelsScreen
import com.example.solarpredict.ui.screens.SettingsScreen
import com.example.solarpredict.ui.screens.SignUpScreen
import com.example.solarpredict.viewmodel.WizardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

private const val WIZARD_GRAPH = "wizard"

@Composable
fun SolarPredictApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = routeShowsBottomBar(currentRoute)

    // --- Live language preference (drives runtime locale switching) ---
    val context = LocalContext.current.applicationContext
    val prefs = remember { SettingsPreferences(context) }
    val languageTag by prefs.language.collectAsState(initial = LANG_EN)
    val appStrings = remember(languageTag) { appStringsFor(languageTag) }

    var pendingLoginMessage by remember { mutableStateOf<String?>(null) }

    val startDestination = remember {
        if (Firebase.auth.currentUser != null) Routes.HOME else Routes.LOGIN
    }

    // --- Auth state listener ---
    //
    // We track the *uid* across changes (not just null/non-null) so that
    // user1 -> user2 transitions are also caught. Firebase fires the
    // listener with currentUser=null briefly during sign-out and then
    // again with the new user during sign-in; if the uid actually
    // changed, every cross-screen cache must be wiped or user2 will see
    // user1's data lingering.
    val lastUidRef = remember { androidx.compose.runtime.mutableStateOf(Firebase.auth.currentUser?.uid) }
    DisposableEffect(navController) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val newUid = auth.currentUser?.uid
            val previousUid = lastUidRef.value
            val current = navController.currentDestination?.route

            if (newUid == null) {
                // Signed out — always send to Login + clear stack + reset
                // every in-memory cache so the next account starts blank.
                com.example.solarpredict.data.GuestStore.reset()
                com.example.solarpredict.data.WizardSession.clear()
                if (current != null && current != Routes.LOGIN && current != Routes.SIGN_UP) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (previousUid != null && previousUid != newUid) {
                // User actually swapped (uid changed) — same cleanup, but
                // route to Home since the new account is signed in.
                com.example.solarpredict.data.GuestStore.reset()
                com.example.solarpredict.data.WizardSession.clear()
                navController.navigate(Routes.HOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }

            lastUidRef.value = newUid
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) SolarBottomNavBar(navController)
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                // --- Auth ---
                composable(Routes.LOGIN) {
                    val msg = pendingLoginMessage
                    androidx.compose.runtime.LaunchedEffect(msg) {
                        if (msg != null) pendingLoginMessage = null
                    }
                    LoginScreen(
                        initialMessage = msg,
                        onAuthSuccess = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onSignUp = {
                            navController.navigate(Routes.SIGN_UP)
                        },
                    )
                }
                composable(Routes.SIGN_UP) {
                    SignUpScreen(
                        onAccountCreated = { email ->
                            pendingLoginMessage = "Account created for $email. Please sign in."
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // --- Bottom-tab destinations ---
                composable(Routes.HOME) {
                    HomeScreen(
                        onNewPrediction = {
                            // Reset any leftover wizard state from a prior prediction
                            // so the user starts each prediction with a clean slate.
                            com.example.solarpredict.data.WizardSession.clear()
                            navController.navigate(Routes.LOCATION)
                        },
                        onOpenPrediction = { id ->
                            navController.navigate(Routes.predictionDetail(id))
                        },
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(
                        onOpenPrediction = { id ->
                            navController.navigate(Routes.predictionDetail(id))
                        },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onOpenPrediction = { id ->
                            navController.navigate(Routes.predictionDetail(id))
                        },
                    )
                }
                composable(
                    route = Routes.PREDICTION_DETAIL_PATTERN,
                    arguments = listOf(navArgument("id") {
                        type = NavType.StringType
                    }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id").orEmpty()
                    PredictionDetailScreen(
                        predictionId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onAccountInfo = { navController.navigate(Routes.ACCOUNT_INFO) },
                        onSavedPanels = { navController.navigate(Routes.SAVED_PANELS) },
                        onSavedLocations = { navController.navigate(Routes.SAVED_LOCATIONS) },
                        onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
                        onSignedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                // --- Settings sub-screens ---
                composable(Routes.ACCOUNT_INFO) {
                    AccountInfoScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SAVED_PANELS) {
                    SavedPanelsScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { id ->
                            navController.navigate(Routes.editPanel(id))
                        },
                    )
                }
                composable(
                    route = Routes.EDIT_PANEL_PATTERN,
                    arguments = listOf(navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")?.takeIf { it.isNotBlank() }
                    EditPanelScreen(
                        panelId = id,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(Routes.SAVED_LOCATIONS) {
                    SavedLocationsScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { navController.navigate(Routes.ADD_LOCATION) },
                        onEdit = { id -> navController.navigate(Routes.editLocation(id)) },
                    )
                }
                composable(Routes.ADD_LOCATION) {
                    AddLocationScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.EDIT_LOCATION_PATTERN,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id").orEmpty()
                    AddLocationScreen(
                        editingId = id.takeIf { it.isNotBlank() },
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }

                // --- Wizard (Location -> Panel Config) ---
                navigation(startDestination = Routes.LOCATION, route = WIZARD_GRAPH) {
                    composable(Routes.LOCATION) { backStackEntry ->
                        val parentEntry = remember(backStackEntry) {
                            navController.getBackStackEntry(WIZARD_GRAPH)
                        }
                        val wizardVM: WizardViewModel = viewModel(
                            viewModelStoreOwner = parentEntry,
                            factory = WizardViewModel.Factory,
                        )
                        LocationScreen(
                            wizardVM = wizardVM,
                            onBack = {
                                // Bailing out of the wizard before reaching
                                // Result must clear the in-flight session,
                                // otherwise stale location/panel could leak
                                // into a later prediction.
                                com.example.solarpredict.data.WizardSession.clear()
                                navController.popBackStack()
                            },
                            onNext = { navController.navigate(Routes.PANEL_CONFIG) },
                        )
                    }
                    composable(Routes.PANEL_CONFIG) { backStackEntry ->
                        val parentEntry = remember(backStackEntry) {
                            navController.getBackStackEntry(WIZARD_GRAPH)
                        }
                        val wizardVM: WizardViewModel = viewModel(
                            viewModelStoreOwner = parentEntry,
                            factory = WizardViewModel.Factory,
                        )
                        PanelConfigScreen(
                            wizardVM = wizardVM,
                            onBack = { navController.popBackStack() },
                            onCalculated = {
                                // Pop the entire wizard graph and land on RESULT.
                                // The wizard inputs survive in WizardSession, so
                                // Result still has the data even though the
                                // graph is gone. Back from Result -> Home (the
                                // user explicitly cannot reopen the wizard tail).
                                navController.navigate(Routes.RESULT) {
                                    popUpTo(WIZARD_GRAPH) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }

                // --- Result (lives outside the wizard graph). Reads its inputs
                //     from the WizardSession singleton, so popping the wizard
                //     graph in onCalculated above does NOT destroy them.
                composable(Routes.RESULT) { entry ->
                    val wizardVM: WizardViewModel = viewModel(
                        viewModelStoreOwner = entry,
                        factory = WizardViewModel.Factory,
                    )
                    ResultScreen(
                        wizardVM = wizardVM,
                        // We deliberately do NOT clear WizardSession here.
                        // Clearing synchronously before navigate() flips
                        // location/panel to null, which made ResultScreen
                        // recompose into the EmptyResultState branch for
                        // one frame — that's the brief "no active
                        // prediction" flash the user reported.
                        //
                        // The next "New Prediction" tap on Home already
                        // clears WizardSession before entering the wizard,
                        // and AuthRepository.signOut() / the auth-state
                        // listener clear it on logout / account swap. So
                        // it is safe to leave stale data in WizardSession
                        // until one of those triggers fires.
                        onBack = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onSaved = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}
