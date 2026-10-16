package com.kudo.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SignatureSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun signaturePreferenceDefaultsOnTogglesAndSurvivesReconstruction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("kudo_preferences", 0)
        val had = prefs.contains("show_signatures")
        val previous = prefs.getBoolean("show_signatures", false)
        try {
            prefs.edit().remove("show_signatures").commit()
            val store = AppSettings(context)
            assertTrue(store.display.value.showSignatures)
            var settings by mutableStateOf(store.display.value)
            compose.setContent { KudoTheme(settings) { SettingsScreen(settings, { settings = it; store.update(it) }, null, {}, {}, {}) } }
            compose.onNodeWithTag("show-signatures").performScrollTo().assertIsOn().performClick().assertIsOff()
            assertFalse(AppSettings(context).display.value.showSignatures)
            compose.onNodeWithTag("show-signatures").performClick().assertIsOn()
            assertTrue(AppSettings(context).display.value.showSignatures)
        } finally { if (had) prefs.edit().putBoolean("show_signatures", previous).commit() else prefs.edit().remove("show_signatures").commit() }
    }
}
