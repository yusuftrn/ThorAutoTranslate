package com.thorautotranslate

import android.view.Display
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(settings: SettingsManager, displays: List<Display>, changeLanguage: (String) -> Unit,
    openAccessibility: () -> Unit, finish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val service by GameTranslationService.state.collectAsState()
    val valid = remember(step, revision, consent) {
        when (step) {
            1 -> settings.mode != TranslationMode.CLOUD || settings.apiKey.isNotBlank()
            2 -> consent && displays.any { it.displayId == settings.sourceDisplay } && displays.any {
                it.displayId != settings.sourceDisplay && (settings.outputDisplay == -1 || it.displayId == settings.outputDisplay)
            }
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.setup_step, step + 1), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        key(step) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when (step) { 0 -> R.string.setup_languages; 1 -> R.string.setup_method; else -> R.string.setup_access }), style = MaterialTheme.typography.titleMedium)
                when (step) {
                    0 -> { InterfaceLanguage(changeLanguage); TranslationLanguages(settings) }
                    1 -> EngineSettings(settings, onboarding = true, changed = { revision++ })
                    2 -> {
                        DisplaySettings(settings, displays, changed = { revision++ })
                        ReadingSettings(settings)
                        HorizontalDivider()
                        Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.disclosure), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(consent, { consent = it })
                            Text(stringResource(R.string.consent), Modifier.weight(1f))
                        }
                        if (service.connected) Text(stringResource(R.string.service_ready))
                        else {
                            Text(stringResource(R.string.access_instruction), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = openAccessibility, enabled = consent) { Text(stringResource(R.string.enable_accessibility)) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { step-- }, enabled = step > 0) { Text(stringResource(R.string.back)) }
            Button(onClick = { if (step < 2) step++ else finish() }, enabled = valid) {
                Text(stringResource(if (step < 2) R.string.next else R.string.finish))
            }
        }
    }
}
