package com.thorautotranslate

import android.content.res.Resources
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.Display
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class GameApp(val packageName: String, val label: String, val isGame: Boolean)

@Composable
fun SettingsScreen(settings: SettingsManager, displays: List<Display>, returnToGame: () -> Unit,
    changeLanguage: (String) -> Unit, restartSetup: () -> Unit, openAccessibility: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val revision = settingsRevision(settings)
    val enabled = remember(revision) { settings.enabled }
    val service by GameTranslationService.state.collectAsState()
    val masterLabel = stringResource(R.string.master_enabled)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Switch(enabled, { settings.enabled = it }, modifier = Modifier.semantics { contentDescription = masterLabel })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(R.string.live, R.string.settings, R.string.apps).forEachIndexed { index, name ->
                TextButton(onClick = { tab = index }) {
                    Text(stringResource(name), color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        when (tab) {
            0 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (!enabled) R.string.paused else if (service.connected) R.string.live else R.string.setup_required), style = MaterialTheme.typography.titleMedium)
                if (enabled && service.status.isNotBlank()) Text(service.status)
                if (!service.connected) {
                    Text(stringResource(R.string.access_instruction))
                    Button(onClick = openAccessibility) { Text(stringResource(R.string.enable_accessibility)) }
                } else if (service.game.isBlank()) {
                    Text(stringResource(R.string.wait_game_hint))
                    TextButton(onClick = { tab = 2 }) { Text(stringResource(R.string.choose_apps)) }
                }
                if (displays.size < 2) Text(stringResource(R.string.lower_missing), color = MaterialTheme.colorScheme.error)
                if (service.game.isNotBlank()) {
                    Text(service.game, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (service.translated.isNotBlank()) Text(service.translated, style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = returnToGame) { Text(stringResource(R.string.return_translation)) }
                }
            }
            1 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InterfaceLanguage(changeLanguage)
                TranslationLanguages(settings)
                ReadingSettings(settings)
                var advanced by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(if (advanced) R.string.hide_advanced else R.string.advanced)) }
                if (advanced) {
                    EngineSettings(settings)
                    DisplaySettings(settings, displays)
                    TextButton(onClick = restartSetup) { Text(stringResource(R.string.restart_setup)) }
                    Text(stringResource(R.string.disclosure), style = MaterialTheme.typography.bodySmall)
                }
            }
            2 -> GameSettings(settings)

        }
    }
}

@Composable
internal fun InterfaceLanguage(changeLanguage: (String) -> Unit) {
    val context = LocalContext.current
    Choice(stringResource(R.string.ui_language), AppLanguage.selected(context), AppLanguage.names.keys.toList(),
        { AppLanguage.names.getValue(it) }, changeLanguage)
}

@Composable
internal fun TranslationLanguages(settings: SettingsManager) {
    var source by remember { mutableStateOf(settings.sourceLanguage) }
    var target by remember { mutableStateOf(settings.targetLanguage) }
    val locale = LocalConfiguration.current.locales[0]
    val languageName: (String) -> String = { Locale.forLanguageTag(it).getDisplayName(locale).replaceFirstChar { c -> c.titlecase(locale) } }
    val sources = remember { listOf("en", "tr", "de", "fr", "es", "it", "pt", "nl", "sv", "da", "no", "fi", "pl", "cs", "ro", "hu", "vi", "id", "ms", "ja", "zh", "ko", "hi") }
    val targets = remember(locale) { TranslateLanguage.getAllLanguages().sortedBy(languageName) }
    Choice(stringResource(R.string.source_language), source, sources, languageName) { source = it; settings.sourceLanguage = it }
    Choice(stringResource(R.string.target_language), target, targets, languageName) { target = it; settings.targetLanguage = it }
}

@Composable
internal fun EngineSettings(settings: SettingsManager, onboarding: Boolean = false, changed: () -> Unit = {}) {
    val revision = settingsRevision(settings)
    val mode = remember(revision) { settings.mode }
    var key by remember { mutableStateOf(settings.apiKey) }
    Text(stringResource(R.string.engine), style = MaterialTheme.typography.titleMedium)
    listOf(TranslationMode.OFFLINE, TranslationMode.FREE, TranslationMode.CLOUD).forEach { option ->
        Row(Modifier.fillMaxWidth().selectable(mode == option, role = Role.RadioButton, onClick = {
            settings.mode = option; changed()
        }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = mode == option, onClick = null)
            Text(stringResource(when (option) { TranslationMode.OFFLINE -> R.string.mode_offline; TranslationMode.FREE -> R.string.mode_free; TranslationMode.CLOUD -> R.string.mode_cloud }), Modifier.padding(start = 12.dp))
        }
    }
    if (mode == TranslationMode.CLOUD) {
        OutlinedTextField(key, {
            key = it
            if (onboarding) { settings.apiKey = it; changed() }
        }, label = { Text(stringResource(R.string.api_key)) }, visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (!onboarding) Button(onClick = { settings.apiKey = key }) { Text(stringResource(R.string.save_key)) }
        if (onboarding && key.isBlank()) Text(stringResource(R.string.key_required), color = MaterialTheme.colorScheme.error)
    }
    Text(stringResource(when (mode) { TranslationMode.OFFLINE -> R.string.offline_notice; TranslationMode.FREE -> R.string.free_notice; TranslationMode.CLOUD -> R.string.cloud_notice }), style = MaterialTheme.typography.bodySmall)
    if (mode == TranslationMode.OFFLINE && !onboarding) LanguageModels(settings)
    Text(stringResource(R.string.metrics_notice), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun ReadingSettings(settings: SettingsManager) {
    var subtitleOnly by remember { mutableStateOf(settings.subtitleOnly) }
    var fontSize by remember { mutableFloatStateOf(settings.fontSize) }
    Text(stringResource(R.string.font_size, fontSize.toInt()))
    Slider(fontSize, { fontSize = it }, onValueChangeFinished = { settings.fontSize = fontSize }, valueRange = 16f..42f)
    Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.subtitle_only), modifier = Modifier.weight(1f))
        Switch(subtitleOnly, { subtitleOnly = it; settings.subtitleOnly = it })
    }
    Text(stringResource(if (subtitleOnly) R.string.subtitle_hint else R.string.full_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun DisplaySettings(settings: SettingsManager, displays: List<Display>, changed: () -> Unit = {}) {
    val resources = LocalResources.current
    var source by remember { mutableIntStateOf(settings.sourceDisplay) }
    var output by remember { mutableIntStateOf(settings.outputDisplay) }
    Choice(stringResource(R.string.source_display), source, displays.map { it.displayId }, { displayLabel(resources, displays, it) }) {
        source = it; settings.sourceDisplay = it; changed()
    }
    Choice(stringResource(R.string.output_display), output, listOf(-1) + displays.map { it.displayId }, {
        if (it == -1) resources.getString(R.string.auto_display) else displayLabel(resources, displays, it)
    }) { output = it; settings.outputDisplay = it; changed() }
    if (source == output) Text(stringResource(R.string.different_displays), color = MaterialTheme.colorScheme.error)
    if (displays.size < 2) Text(stringResource(R.string.lower_missing), color = MaterialTheme.colorScheme.error)
}

private fun displayLabel(resources: Resources, displays: List<Display>, id: Int): String {
    val display = displays.firstOrNull { it.displayId == id } ?: return resources.getString(R.string.display_missing, id)
    return if (id == Display.DEFAULT_DISPLAY) resources.getString(R.string.upper_display, id)
        else resources.getString(R.string.other_display, id, display.name)
}

@Composable
private fun GameSettings(settings: SettingsManager) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<GameApp>>(emptyList()) }
    val enabled = remember { mutableStateMapOf<String, Boolean>() }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != context.packageName }
                .map { GameApp(it.packageName, it.loadLabel(context.packageManager).toString(),
                    it.category == ApplicationInfo.CATEGORY_GAME || it.flags and ApplicationInfo.FLAG_IS_GAME != 0) }
                .sortedWith(compareByDescending<GameApp> { settings.isAppEnabled(it.packageName, it.isGame) }.thenBy { it.label.lowercase() })
        }
    }
    Column {
        Text(stringResource(R.string.apps_hint), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_apps)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn {
            items(apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(enabled[app.packageName] ?: settings.isAppEnabled(app.packageName, app.isGame), {
                        enabled[app.packageName] = it; settings.setAppEnabled(app.packageName, it)
                    })
                }
            }
        }
    }
}

@Composable
internal fun <T> Choice(label: String, selected: T, options: List<T>, name: (T) -> String, onSelected: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.choice_value, label, name(selected)), modifier = Modifier.fillMaxWidth())
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        LazyColumn { items(options.size) { index ->
            TextButton(onClick = { open = false; onSelected(options[index]) }, modifier = Modifier.fillMaxWidth()) {
                Text(name(options[index]), modifier = Modifier.fillMaxWidth())
            }
        } }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } })
}
