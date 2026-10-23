package com.thorautotranslate

import android.content.Context
import androidx.compose.runtime.*
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.thorautotranslate.ui.theme.ThorAutoTranslateTheme

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_SETTINGS = "open_settings" }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) { runCatching { cleanupRetiredModels(applicationContext) } }
        val preferences = SettingsManager(this)
        val displays = getSystemService(DisplayManager::class.java).displays
        val lower = displays.firstOrNull { it.displayId != preferences.sourceDisplay &&
            (preferences.outputDisplay == -1 || it.displayId == preferences.outputDisplay) }
        if (lower != null && display?.displayId != lower.displayId && !intent.getBooleanExtra("lower_screen", false)) {
            val moved = runCatching {
                startActivity(Intent(this, MainActivity::class.java).putExtra("lower_screen", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(lower.displayId).toBundle())
            }.isSuccess
            if (moved) { finish(); return }
        }
        setContent {
            ThorAutoTranslateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var setupComplete by remember { mutableStateOf(preferences.onboardingComplete) }
                    val changeLanguage: (String) -> Unit = {
                        AppLanguage.select(this, it)
                        if (android.os.Build.VERSION.SDK_INT < 33) recreate()
                    }
                    val service by GameTranslationService.state.collectAsState()
                    val settingsActivation = remember {
                        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) service.activationId else null
                    }
                    val enterTranslation = shouldEnterTranslation(setupComplete, preferences.enabled,
                        service.game.isNotBlank(), service.activationId, settingsActivation)
                    LaunchedEffect(enterTranslation) { if (enterTranslation) finish() }
                    val accessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    if (setupComplete) {
                        SettingsScreen(preferences, displays.toList(), returnToGame = { finish() },
                            changeLanguage = changeLanguage, restartSetup = { preferences.onboardingComplete = false; setupComplete = false }, openAccessibility = accessibility)
                    } else {
                        OnboardingScreen(preferences, displays.toList(), changeLanguage, accessibility) {
                            preferences.onboardingComplete = true
                            setupComplete = true
                        }
                    }
                }
            }
        }
    }
}
