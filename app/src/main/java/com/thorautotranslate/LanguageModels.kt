package com.thorautotranslate

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
internal fun settingsRevision(settings: SettingsManager): Int {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return revision
}

@Composable
internal fun LanguageModels(settings: SettingsManager) {
    val revision = settingsRevision(settings)
    val scope = rememberCoroutineScope()
    val manager = remember { com.google.mlkit.common.model.RemoteModelManager.getInstance() }
    var installed by remember { mutableStateOf<Set<com.google.mlkit.nl.translate.TranslateRemoteModel>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<com.google.mlkit.nl.translate.TranslateRemoteModel?>(null) }
    val required = listOf(settings.sourceLanguage, settings.targetLanguage).distinct().filter { it != "en" }
    val ready = required.all { language -> installed.any { it.language == language } }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val languageName: (String) -> String = { java.util.Locale.forLanguageTag(it).getDisplayLanguage(locale) }
    suspend fun refresh() { installed = manager.getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java).await() }
    LaunchedEffect(revision) { runCatching { refresh() }.onFailure { failed = true } }
    Text("ML Kit", style = MaterialTheme.typography.titleSmall)
    if (settings.mode == TranslationMode.OFFLINE) Text(stringResource(R.string.model_selected), color = MaterialTheme.colorScheme.primary)
    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.model_downloading)) }
    if (failed) Text(stringResource(R.string.model_download_failed), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy && (!ready || settings.mode != TranslationMode.OFFLINE), onClick = {
            scope.launch {
                busy = true; failed = false
                settings.mode = TranslationMode.OFFLINE
                try {
                    for (language in required) manager.download(com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(language).build(),
                        com.google.mlkit.common.model.DownloadConditions.Builder().requireWifi().build()).await()
                    refresh()
                } catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(if (ready) R.string.model_use else R.string.model_download)) }
    }
    for (model in installed.sortedBy { languageName(it.language) }) Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(languageName(model.language), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { deleting = model }, enabled = !busy) { Text(stringResource(R.string.model_delete)) }
    }
    deleting?.let { model -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.model_delete_title, languageName(model.language))) },
        text = { Text(stringResource(R.string.mlkit_delete_notice)) },
        confirmButton = { TextButton(onClick = {
            deleting = null
            scope.launch {
                busy = true; failed = false
                if (settings.mode == TranslationMode.OFFLINE && model.language in required) settings.enabled = false
                try { manager.deleteDownloadedModel(model).await(); refresh() }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.model_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.model_cancel)) } }) }
}
