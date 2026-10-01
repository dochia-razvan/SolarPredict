package com.example.solarpredict.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.example.solarpredict.data.LANG_RO

/**
 * Runtime-translatable strings for the entire app.
 *
 * Why this exists instead of res/values-ro/strings.xml:
 *   The Android system applies res-locale strings only when the app process
 *   restarts (or recreate() is invoked) — that creates a visible flash and
 *   loses transient state. The user spec requires the language toggle to
 *   apply *immediately* to every screen without restart, so we drive the
 *   strings through a CompositionLocal that recomposes the moment the
 *   language preference changes.
 *
 * Usage in a screen:  val s = LocalAppStrings.current ; Text(s.signIn)
 *
 * The language tag (e.g. "en", "ro") is persisted via SettingsPreferences
 * and observed by SolarPredictApp at the composition root, which calls
 * [appStringsFor] and provides the result through [LocalAppStrings].
 */
data class AppStrings(

    // --- Generic ---
    val appName: String,
    val back: String,
    val cancel: String,
    val save: String,
    val delete: String,
    val edit: String,
    val ok: String,
    val close: String,
    val loading: String,
    val retry: String,
    val errorPrefix: String,
    val continueLabel: String,

    // --- Auth: Login ---
    val signIn: String,
    val email: String,
    val password: String,
    val forgotPassword: String,
    val createAccount: String,
    val continueAsGuest: String,
    val tagline: String,
    val resetPassword: String,
    val resetPasswordHelper: String,
    val send: String,

    // --- Auth: Sign Up ---
    val signUpTitle: String,
    val signUpHelper: String,
    val username: String,
    val confirmPassword: String,
    val passwordHelper: String,
    val signUp: String,
    val backToSignIn: String,

    // --- Bottom navigation ---
    val home: String,
    val favorites: String,
    val history: String,
    val settings: String,

    // --- Home ---
    val solarPredictTitle: String,
    val helloThere: String,
    val goodMorning: String,
    val goodAfternoon: String,
    val goodEvening: String,
    val guestModeBanner: String,
    val checkYourEnergyInsights: String,
    val lastPrediction: String,
    val noPredictionsYet: String,
    val tapNewPredictionToStart: String,
    val newPrediction: String,
    val recent: String,
    val recentPredictionsAppearHere: String,
    val today: String,
    val yesterday: String,

    // --- Settings ---
    val settingsTitle: String,
    val guestMode: String,
    val signedOut: String,
    val accountInformation: String,
    val savedPanels: String,
    val savedLocations: String,
    val createAccountAction: String,
    val language: String,
    val logOut: String,
    val exitGuestMode: String,

    // --- Account information ---
    val saveUsername: String,
    val changePassword: String,
    val passwordResetSent: String,

    // --- Saved Panels ---
    val savedPanelsTitle: String,
    val noSavedPanels: String,
    val tapAddNewPanel: String,
    val addNewPanel: String,
    val deletePanelTitle: String,
    val deleteConfirmFmt: (name: String) -> String,
    val savedPanelsGuestBanner: String,

    // --- Edit Panel ---
    val addPanelTitle: String,
    val editPanelTitle: String,
    val panelNameRequired: String,
    val panelName: String,
    val efficiencyPercent: String,
    val areaM2: String,
    val saveChanges: String,
    val addPanel: String,

    // --- Saved Locations ---
    val savedLocationsTitle: String,
    val noSavedLocations: String,
    val addNewLocation: String,
    val locationsGuestBanner: String,
    val deleteLocationTitle: String,
    val deleteLocationConfirmFmt: (name: String) -> String,

    // --- Add / Edit Location ---
    val addLocation: String,
    val editLocation: String,
    val locationName: String,
    val locationNameOptional: String,
    val searchPlace: String,
    val saveLocationOption: String,
    val saveLocationHelper: String,
    val useMyCurrentLocation: String,
    val latitude: String,
    val longitude: String,
    val nameRequired: String,
    val locationNameExists: String,
    val coordsLatRange: String,
    val coordsLngRange: String,

    // --- Wizard / Location step ---
    val stepOneOfTwo: String,
    val stepTwoOfTwo: String,
    val searchAddress: String,
    val noSuggestionsYet: String,
    val useSavedLocation: String,
    val pickFromSaved: String,
    val next: String,
    val tapMapToPlace: String,

    // --- Panel Config step ---
    val panelConfiguration: String,
    val configureCustom: String,
    val savePanelForFutureUse: String,
    val savePanelHelper: String,
    val useSavedPanel: String,
    val noSavedPanelsYet: String,
    val panelsSavedFmt: (count: Int) -> String,
    val saveChangesToPanel: String,
    val continueHelper: String,

    // --- Result Screen ---
    val resultTitle: String,
    val runningPrediction: String,
    val expectedEnergyProduction: String,
    val confidenceInterval82: String,
    val lowestEstimate: String,
    val highestEstimate: String,
    val hourlyProduction: String,
    val noDataForRange: String,
    val next24h: String,
    val next7d: String,
    val next14d: String,
    val custom: String,
    val customRange: String,
    val customRangeHelper: String,
    val start: String,
    val end: String,
    val apply: String,
    val endMustBeAfterStart: String,
    val maxRangeDays: String,
    val tapToSeeValue: String,
    val markFavorite: String,
    val weatherSnapshot: String,
    val location: String,
    val panel: String,

    // --- History ---
    val historyTitle: String,
    val historyEmpty: String,
    val tapBoltToStart: String,
    val expected: String,

    // --- Favorites ---
    val favoritesTitle: String,
    val noFavorites: String,
    val tapStarToFavorite: String,
    val removeFromFavorites: String,
    val removeFavoriteWarning: String,
    val remove: String,
    val favoritesGuestBanner: String,

    // --- Prediction detail ---
    val predictionDetailTitle: String,
    val predictionLoading: String,
    val predictionNotFound: String,
    val savedOn: String,
    val noActivePrediction: String,
    val startNewPrediction: String,
    val goToHome: String,

    // --- Errors ---
    val couldNotFindAddress: String,
    val networkError: String,
    val mustBeSignedIn: String,
)

private val English = AppStrings(
    // Generic
    appName = "SolarPredict",
    back = "Back",
    cancel = "Cancel",
    save = "Save",
    delete = "Delete",
    edit = "Edit",
    ok = "OK",
    close = "Close",
    loading = "Loading…",
    retry = "Retry",
    errorPrefix = "Error",
    continueLabel = "Continue",

    // Auth: Login
    signIn = "Sign In",
    email = "Email",
    password = "Password",
    forgotPassword = "Forgot Password?",
    createAccount = "Create Account",
    continueAsGuest = "Continue as Guest",
    tagline = "Predict your solar production",
    resetPassword = "Reset Password",
    resetPasswordHelper = "Enter your email address. We'll send you a link to reset your password.",
    send = "Send",

    // Auth: Sign Up
    signUpTitle = "Create Account",
    signUpHelper = "Sign up to start predicting your solar production.",
    username = "Username",
    confirmPassword = "Confirm Password",
    passwordHelper = "8+ chars, 1 uppercase, 1 number, 1 special character",
    signUp = "Sign Up",
    backToSignIn = "Back to Sign In",

    // Bottom nav
    home = "Home",
    favorites = "Favorites",
    history = "History",
    settings = "Settings",

    // Home
    solarPredictTitle = "SolarPredict",
    helloThere = "there",
    goodMorning = "Good morning",
    goodAfternoon = "Good afternoon",
    goodEvening = "Good evening",
    guestModeBanner = "You're using guest mode.",
    checkYourEnergyInsights = "Let's check your energy insights.",
    lastPrediction = "LAST PREDICTION",
    noPredictionsYet = "No predictions yet",
    tapNewPredictionToStart = "Tap New Prediction to start.",
    newPrediction = "New Prediction",
    recent = "Recent",
    recentPredictionsAppearHere = "Your recent predictions will appear here.",
    today = "Today",
    yesterday = "Yesterday",

    // Settings
    settingsTitle = "Settings",
    guestMode = "Guest mode",
    signedOut = "Signed out",
    accountInformation = "Account Information",
    savedPanels = "Saved Panels",
    savedLocations = "Saved Locations",
    createAccountAction = "Create Account",
    language = "Language",
    logOut = "Log Out",
    exitGuestMode = "Exit Guest Mode",

    // Account
    saveUsername = "Save Username",
    changePassword = "Change Password",
    passwordResetSent = "Password reset email sent.",

    // Saved Panels
    savedPanelsTitle = "Saved Panels",
    noSavedPanels = "No saved panels yet.",
    tapAddNewPanel = "Tap Add New Panel to create one.",
    addNewPanel = "Add New Panel",
    deletePanelTitle = "Delete panel?",
    deleteConfirmFmt = { "This will remove \"$it\" permanently." },
    savedPanelsGuestBanner = "You are using the app as a guest. Your saved panels will be lost when you close the app. Create an account to keep your data.",

    // Edit Panel
    addPanelTitle = "Add Panel",
    editPanelTitle = "Edit Panel",
    panelNameRequired = "Panel name *",
    panelName = "Panel name",
    efficiencyPercent = "Efficiency (%)",
    areaM2 = "Area (m²)",
    saveChanges = "Save Changes",
    addPanel = "Add Panel",

    // Saved Locations
    savedLocationsTitle = "Saved Locations",
    noSavedLocations = "No saved locations yet.",
    addNewLocation = "Add New Location",
    locationsGuestBanner = "You are using the app as a guest. Your saved locations will be lost when you close the app. Create an account to keep your data.",
    deleteLocationTitle = "Delete location?",
    deleteLocationConfirmFmt = { "This will remove \"$it\" permanently." },

    // Add / Edit Location
    addLocation = "Add Location",
    editLocation = "Edit Location",
    locationName = "Location name",
    locationNameOptional = "Location name (optional)",
    searchPlace = "Search a place",
    saveLocationOption = "Save this location",
    saveLocationHelper = "Quick access for future predictions",
    useMyCurrentLocation = "Use my current location",
    latitude = "Latitude",
    longitude = "Longitude",
    nameRequired = "Name is required",
    locationNameExists = "A location with this name already exists",
    coordsLatRange = "Latitude must be between -90 and 90",
    coordsLngRange = "Longitude must be between -180 and 180",

    // Wizard / Location step
    stepOneOfTwo = "Step 1 of 2: Choose a Location",
    stepTwoOfTwo = "Step 2 of 2: Configure Your Panel",
    searchAddress = "Search address",
    noSuggestionsYet = "Type to search…",
    useSavedLocation = "Use Saved Location",
    pickFromSaved = "Pick from your saved locations",
    next = "Next",
    tapMapToPlace = "Tap the map to place a pin",

    // Panel Config step
    panelConfiguration = "Panel Configuration",
    configureCustom = "Configure a custom panel",
    savePanelForFutureUse = "Save this panel for future use",
    savePanelHelper = "When off, the panel name is optional.",
    useSavedPanel = "Use Saved Panel",
    noSavedPanelsYet = "No saved panels yet — add one first",
    panelsSavedFmt = { count -> "$count panel${if (count == 1) "" else "s"} saved" },
    saveChangesToPanel = "Save changes to this panel",
    continueHelper = "We'll fetch live weather and run the prediction on the next screen.",

    // Result
    resultTitle = "Result",
    runningPrediction = "Running prediction…",
    expectedEnergyProduction = "Expected energy production",
    confidenceInterval82 = "77% CONFIDENCE INTERVAL",
    lowestEstimate = "Lowest estimated production",
    highestEstimate = "Highest estimated production",
    hourlyProduction = "Production over time (kWh)",
    noDataForRange = "No data for this range",
    next24h = "Next 24h",
    next7d = "Next 7d",
    next14d = "Next 14d",
    custom = "Custom",
    customRange = "Custom Range",
    customRangeHelper = "Pick a start and end. Maximum 14-day span.",
    start = "Start",
    end = "End",
    apply = "Apply",
    endMustBeAfterStart = "End must be after start",
    maxRangeDays = "Maximum range is 14 days",
    tapToSeeValue = "Drag along the line to see the value at each hour.",
    markFavorite = "Mark as favorite",
    weatherSnapshot = "Weather",
    location = "Location",
    panel = "Panel",

    // History
    historyTitle = "History",
    historyEmpty = "No predictions yet.",
    tapBoltToStart = "Tap to get started.",
    expected = "Expected",

    // Favorites
    favoritesTitle = "Favorites",
    noFavorites = "No favorites yet.",
    tapStarToFavorite = "Tap the star on a prediction to favorite it.",
    removeFromFavorites = "Remove from favorites?",
    removeFavoriteWarning = "This will not delete the prediction from history.",
    remove = "Remove",
    favoritesGuestBanner = "You are using the app as a guest. Your favorites will be lost when you close the app. Create an account to keep your data.",

    // Prediction detail
    predictionDetailTitle = "Prediction Details",
    predictionLoading = "Loading prediction…",
    predictionNotFound = "Prediction not found.",
    savedOn = "Saved on",
    noActivePrediction = "No active prediction. Start a new one to see results here.",
    startNewPrediction = "Start a new prediction",
    goToHome = "Go to Home",

    // Errors
    couldNotFindAddress = "Could not find that address",
    networkError = "Network error. Please try again.",
    mustBeSignedIn = "You must be signed in to use this feature.",
)

private val Romanian = AppStrings(
    // Generic
    appName = "SolarPredict",
    back = "Înapoi",
    cancel = "Anulează",
    save = "Salvează",
    delete = "Șterge",
    edit = "Modifică",
    ok = "OK",
    close = "Închide",
    loading = "Se încarcă…",
    retry = "Reîncearcă",
    errorPrefix = "Eroare",
    continueLabel = "Continuă",

    // Auth: Login
    signIn = "Autentificare",
    email = "E-mail",
    password = "Parolă",
    forgotPassword = "Ai uitat parola?",
    createAccount = "Creează cont",
    continueAsGuest = "Continuă ca invitat",
    tagline = "Estimează-ți producția solară",
    resetPassword = "Resetare parolă",
    resetPasswordHelper = "Introdu adresa de e-mail. Îți vom trimite un link pentru a-ți reseta parola.",
    send = "Trimite",

    // Auth: Sign Up
    signUpTitle = "Creează cont",
    signUpHelper = "Înregistrează-te pentru a estima producția solară.",
    username = "Nume utilizator",
    confirmPassword = "Confirmă parola",
    passwordHelper = "Min. 8 caractere, 1 majusculă, 1 cifră, 1 caracter special",
    signUp = "Înregistrare",
    backToSignIn = "Înapoi la autentificare",

    // Bottom nav
    home = "Acasă",
    favorites = "Favorite",
    history = "Istoric",
    settings = "Setări",

    // Home
    solarPredictTitle = "SolarPredict",
    helloThere = "prieten",
    goodMorning = "Bună dimineața",
    goodAfternoon = "Bună ziua",
    goodEvening = "Bună seara",
    guestModeBanner = "Folosești modul invitat.",
    checkYourEnergyInsights = "Să verificăm producția ta solară.",
    lastPrediction = "ULTIMA PREDICȚIE",
    noPredictionsYet = "Nu există încă predicții",
    tapNewPredictionToStart = "Apasă „Predicție nouă\" pentru a începe.",
    newPrediction = "Predicție nouă",
    recent = "Recente",
    recentPredictionsAppearHere = "Predicțiile recente vor apărea aici.",
    today = "Astăzi",
    yesterday = "Ieri",

    // Settings
    settingsTitle = "Setări",
    guestMode = "Mod invitat",
    signedOut = "Deconectat",
    accountInformation = "Date cont",
    savedPanels = "Panouri salvate",
    savedLocations = "Locații salvate",
    createAccountAction = "Creează cont",
    language = "Limbă",
    logOut = "Deconectare",
    exitGuestMode = "Ieși din modul invitat",

    // Account
    saveUsername = "Salvează numele",
    changePassword = "Schimbă parola",
    passwordResetSent = "E-mailul de resetare a parolei a fost trimis.",

    // Saved Panels
    savedPanelsTitle = "Panouri salvate",
    noSavedPanels = "Nu ai încă panouri salvate.",
    tapAddNewPanel = "Apasă „Adaugă panou\" pentru a crea unul.",
    addNewPanel = "Adaugă panou",
    deletePanelTitle = "Ștergi panoul?",
    deleteConfirmFmt = { "Această acțiune va elimina definitiv „$it\"." },
    savedPanelsGuestBanner = "Folosești aplicația ca invitat. Panourile salvate se pierd la închiderea aplicației. Creează un cont pentru a-ți păstra datele.",

    // Edit Panel
    addPanelTitle = "Adaugă panou",
    editPanelTitle = "Modifică panoul",
    panelNameRequired = "Nume panou *",
    panelName = "Nume panou",
    efficiencyPercent = "Eficiență (%)",
    areaM2 = "Suprafață (m²)",
    saveChanges = "Salvează modificările",
    addPanel = "Adaugă panou",

    // Saved Locations
    savedLocationsTitle = "Locații salvate",
    noSavedLocations = "Nu ai încă locații salvate.",
    addNewLocation = "Adaugă locație",
    locationsGuestBanner = "Folosești aplicația ca invitat. Locațiile salvate se pierd la închiderea aplicației. Creează un cont pentru a-ți păstra datele.",
    deleteLocationTitle = "Ștergi locația?",
    deleteLocationConfirmFmt = { "Această acțiune va elimina definitiv „$it\"." },

    // Add / Edit Location
    addLocation = "Adaugă locație",
    editLocation = "Modifică locația",
    locationName = "Nume locație",
    locationNameOptional = "Nume locație (opțional)",
    searchPlace = "Caută o adresă",
    saveLocationOption = "Salvează această locație",
    saveLocationHelper = "Acces rapid pentru predicții viitoare",
    useMyCurrentLocation = "Folosește locația mea",
    latitude = "Latitudine",
    longitude = "Longitudine",
    nameRequired = "Numele este obligatoriu",
    locationNameExists = "Există deja o locație cu acest nume",
    coordsLatRange = "Latitudinea trebuie să fie între -90 și 90",
    coordsLngRange = "Longitudinea trebuie să fie între -180 și 180",

    // Wizard / Location step
    stepOneOfTwo = "Pasul 1 din 2: Alege o locație",
    stepTwoOfTwo = "Pasul 2 din 2: Configurează panoul",
    searchAddress = "Caută adresă",
    noSuggestionsYet = "Tastează pentru a căuta…",
    useSavedLocation = "Folosește locație salvată",
    pickFromSaved = "Alege din locațiile salvate",
    next = "Continuă",
    tapMapToPlace = "Atinge harta pentru a fixa pinul",

    // Panel Config step
    panelConfiguration = "Configurare panou",
    configureCustom = "Configurează un panou personalizat",
    savePanelForFutureUse = "Salvează acest panou pentru utilizare viitoare",
    savePanelHelper = "Când e dezactivat, numele panoului este opțional.",
    useSavedPanel = "Folosește panou salvat",
    noSavedPanelsYet = "Nu ai panouri salvate — adaugă unul mai întâi",
    panelsSavedFmt = { count -> "$count ${if (count == 1) "panou salvat" else "panouri salvate"}" },
    saveChangesToPanel = "Salvează modificările acestui panou",
    continueHelper = "Vom prelua datele meteo în direct și vom rula predicția pe ecranul următor.",

    // Result
    resultTitle = "Rezultat",
    runningPrediction = "Se rulează predicția…",
    expectedEnergyProduction = "Producția de energie estimată",
    confidenceInterval82 = "INTERVAL DE ÎNCREDERE 77%",
    lowestEstimate = "Cea mai mică producție estimată",
    highestEstimate = "Cea mai mare producție estimată",
    hourlyProduction = "Producție în timp (kWh)",
    noDataForRange = "Nu există date pentru acest interval",
    next24h = "24h",
    next7d = "7 zile",
    next14d = "14 zile",
    custom = "Personalizat",
    customRange = "Interval personalizat",
    customRangeHelper = "Alege un început și un sfârșit. Maxim 14 zile.",
    start = "Început",
    end = "Sfârșit",
    apply = "Aplică",
    endMustBeAfterStart = "Sfârșitul trebuie să fie după început",
    maxRangeDays = "Intervalul maxim este 14 zile",
    tapToSeeValue = "Glisează pe linie pentru a vedea valoarea la fiecare oră.",
    markFavorite = "Marchează ca favorit",
    weatherSnapshot = "Vreme",
    location = "Locație",
    panel = "Panou",

    // History
    historyTitle = "Istoric",
    historyEmpty = "Nu există încă predicții.",
    tapBoltToStart = "Apasă pentru a începe.",
    expected = "Estimat",

    // Favorites
    favoritesTitle = "Favorite",
    noFavorites = "Nu ai încă favorite.",
    tapStarToFavorite = "Apasă pe steluță într-o predicție pentru a o adăuga la favorite.",
    removeFromFavorites = "Elimini din favorite?",
    removeFavoriteWarning = "Predicția nu va fi ștearsă din istoric.",
    remove = "Elimină",
    favoritesGuestBanner = "Folosești aplicația ca invitat. Favoritele se pierd la închiderea aplicației. Creează un cont pentru a-ți păstra datele.",

    // Prediction detail
    predictionDetailTitle = "Detalii predicție",
    predictionLoading = "Se încarcă predicția…",
    predictionNotFound = "Predicția nu a fost găsită.",
    savedOn = "Salvat pe",
    noActivePrediction = "Nu ai o predicție activă. Începe una nouă pentru a vedea rezultatele aici.",
    startNewPrediction = "Începe o predicție nouă",
    goToHome = "Acasă",

    // Errors
    couldNotFindAddress = "Adresa nu a putut fi găsită",
    networkError = "Eroare de rețea. Încearcă din nou.",
    mustBeSignedIn = "Trebuie să fii autentificat pentru a folosi această funcție.",
)

fun appStringsFor(languageTag: String): AppStrings =
    if (languageTag == LANG_RO) Romanian else English

/**
 * Composition local that gives every screen access to the right [AppStrings]
 * for the user's current language. The default value is English so previews
 * keep working without explicit wiring.
 */
val LocalAppStrings: ProvidableCompositionLocal<AppStrings> =
    compositionLocalOf { English }

@Composable
fun rememberAppStrings(): AppStrings = LocalAppStrings.current
