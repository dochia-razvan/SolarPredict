package com.example.solarpredict

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.solarpredict.data.SettingsPreferences
import com.example.solarpredict.nav.SolarPredictApp
import com.example.solarpredict.ui.theme.SolarPredictTheme
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

class MainActivity : ComponentActivity() {

    /**
     * Apply the user's saved language tag (English or Romanian) before the
     * Compose tree is created so all string resources resolve in the right locale.
     *
     * We use runBlocking on a single DataStore read on attachBaseContext, which
     * happens once at activity start. This is acceptable for a quick prefs read
     * and avoids first-frame flicker / locale flash.
     */
    override fun attachBaseContext(newBase: Context) {
        val prefs = SettingsPreferences(newBase.applicationContext)
        val tag = runBlocking { prefs.language.first() }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPlaces()
        enableEdgeToEdge()
        setContent {
            SolarPredictTheme {
                SolarPredictApp()
            }
        }
    }

    /**
     * Initialise the Places SDK once per process. The API key is the same one
     * the Maps SDK uses, injected by Gradle from local.properties as the
     * `com.google.android.geo.API_KEY` manifest meta-data entry. Reading it
     * back from PackageManager keeps everything in one place — there is no
     * second copy of the key to keep in sync.
     */
    private fun initPlaces() {
        if (Places.isInitialized()) return
        val apiKey = runCatching {
            val info = packageManager.getApplicationInfo(
                packageName, PackageManager.GET_META_DATA,
            )
            info.metaData?.getString("com.google.android.geo.API_KEY")
        }.getOrNull().orEmpty()
        if (apiKey.isNotBlank()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
    }
}
