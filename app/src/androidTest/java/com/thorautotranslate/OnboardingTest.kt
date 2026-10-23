package com.thorautotranslate

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.thorautotranslate.ui.theme.ThorAutoTranslateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class OnboardingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cloudNeedsAKeyAndScreenReadingNeedsExplicitConsent() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "onboarding-test"
        val settings = SettingsManager(app, name)
        settings.prefs.edit().clear().commit()
        var finished = false
        try {
            assertFalse(settings.onboardingComplete)
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
                val context = LocalContext.current.createConfigurationContext(configuration)
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                    ThorAutoTranslateTheme {
                        OnboardingScreen(settings, app.getSystemService(DisplayManager::class.java).displays.toList(), {}, {}) {
                            settings.onboardingComplete = true
                            finished = true
                        }
                    }
                }
            }
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Google Cloud · own API key").performScrollTo().performClick()
            compose.onNodeWithText("Continue").assertIsNotEnabled()
            compose.onNodeWithText("Google Cloud API key").performTextInput("test-key-never-used")
            compose.onNodeWithText("Continue").assertIsEnabled()
            compose.onNodeWithText("On device · no API key").performScrollTo().performClick()
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Finish setup").assertIsNotEnabled()
            compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()
            compose.onNodeWithText("Finish setup").assertIsEnabled().performClick()
            compose.runOnIdle { assertTrue(finished); assertTrue(settings.onboardingComplete) }
            assertEquals(TranslationMode.OFFLINE, SettingsManager(app, name).mode)
        } finally { settings.prefs.edit().clear().commit() }
    }


    @Test fun allNineUiLanguagesHaveTheirOwnResourcesAndAnAdaptiveIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf("en" to "Settings", "tr" to "Ayarlar", "de" to "Einstellungen", "es" to "Ajustes",
            "fr" to "Réglages", "pt" to "Configurações", "ja" to "設定", "ko" to "설정", "zh" to "设置")
        expected.forEach { (language, text) ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
            assertEquals(text, localized.getString(R.string.settings))
            assertTrue(localized.getString(R.string.disclosure).length > 100)
            assertFalse(localized.getString(R.string.history_hint).isBlank())
            assertFalse(localized.getString(R.string.http_error, 429).contains("%1"))
        }
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val bitmap = android.graphics.Bitmap.createBitmap(1024, 1024, android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 1024, 1024)
        icon.draw(android.graphics.Canvas(bitmap))
        context.openFileOutput("icon-preview.png", Context.MODE_PRIVATE).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
