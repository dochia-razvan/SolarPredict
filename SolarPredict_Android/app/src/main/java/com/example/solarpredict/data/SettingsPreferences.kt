package com.example.solarpredict.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "solar_settings")
private val LANGUAGE_KEY = stringPreferencesKey("language_tag")

const val LANG_EN = "en"
const val LANG_RO = "ro"

class SettingsPreferences(private val context: Context) {

    val language: Flow<String> =
        context.settingsDataStore.data.map { it[LANGUAGE_KEY] ?: LANG_EN }

    suspend fun setLanguage(tag: String) {
        context.settingsDataStore.edit { it[LANGUAGE_KEY] = tag }
    }
}
