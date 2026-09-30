package com.example.solarpredict.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solarpredict.data.PanelConfig
import com.example.solarpredict.data.SavedLocation
import com.example.solarpredict.data.WizardSession
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin ViewModel facade over [WizardSession]. The data lives in the singleton
 * so it survives the wizard graph being popped when we transition to Result.
 */
class WizardViewModel : ViewModel() {

    val location: StateFlow<SavedLocation?> = WizardSession.location
    val panel: StateFlow<PanelConfig?> = WizardSession.panel

    fun setLocation(loc: SavedLocation) { WizardSession.setLocation(loc) }
    fun setPanel(p: PanelConfig) { WizardSession.setPanel(p) }

    companion object {
        val Factory = viewModelFactory {
            initializer { WizardViewModel() }
        }
    }
}
