package com.thorautotranslate

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.app.KeyguardManager
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import android.os.SystemClock
import kotlinx.coroutines.asExecutor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ServiceState(
    val connected: Boolean = false,
    val game: String = "",
    val status: String = "",
    val original: String = "",
    val translated: String = "",
    val activationId: Long = 0L,
    val latencyMs: Long = 0L
)

class GameTranslationService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var foregroundGame: String? = null
    private var activationId = 0L
    private var messages: Context? = null
    private fun message(id: Int, vararg args: Any): String = (messages ?: this).getString(id, *args)
    private lateinit var settings: SettingsManager
    private lateinit var displays: DisplayManager
    private val translationHistory = TranslationHistory()
    private var displayedSequence = 0L
    private var panel: TranslationPanel? = null
    private var panelDisplay = -1
    private var backdrop: Bitmap? = null
    private var activePackage: String? = null
    private var gameLabel = ""
    private var generation = 0
    private var frameJob: Job? = null
    private var pipeline: LiveTranslationPipeline? = null
    private var retryJob: Job? = null
    private val screenshotExecutor = Dispatchers.Default.asExecutor()
    private var captureIntervalMs = 350L
    private var analyzer: TextRecognitionAnalyzer? = null
    private var engine: TranslationManager? = null
    private var tracker = DialogueTracker()
    private var empty = false
    private val appEligibility = mutableMapOf<String, Boolean>()

    override fun onServiceConnected() {
        scope.launch(Dispatchers.IO) { runCatching { cleanupRetiredModels(applicationContext) } }
        monitorJob?.cancel()
        if (::settings.isInitialized) {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
            stopSession(message(R.string.settings_updated))
        }
        messages = AppLanguage.wrap(this)
        settings = SettingsManager(this)
        displays = getSystemService(DisplayManager::class.java)
        settings.prefs.registerOnSharedPreferenceChangeListener(this)
        _state.value = ServiceState(connected = true, status = message(R.string.waiting_game))
        monitorJob = scope.launch {
            while (isActive) {
                try { synchronizeGame() }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { stopSession(message(R.string.screen_error, error.javaClass.simpleName)) }
                delay(200)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stopSession(message(R.string.service_interrupted)) }

    private fun topPackage(displayId: Int): String? {
        val windows = windowsOnAllDisplays[displayId].orEmpty()
        if (windows.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isFocused }) return null
        val window = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }.firstOrNull { info ->
                val bounds = Rect()
                info.getBoundsInScreen(bounds)
                !bounds.isEmpty
            } ?: return null
        val root = window.root ?: return null
        return root.packageName?.toString().also { root.recycle() }
    }

    private fun isGame(name: String): Boolean = appEligibility.getOrPut(name) {
        if (name == packageName) false else runCatching {
            val info = packageManager.getApplicationInfo(name, 0)
            settings.isAppEnabled(name, info.category == ApplicationInfo.CATEGORY_GAME ||
                info.flags and ApplicationInfo.FLAG_IS_GAME != 0)
        }.getOrDefault(false)
    }

    private fun synchronizeGame() {
        if (!settings.onboardingComplete) { foregroundGame = null; stopSession(message(R.string.setup_required)); return }
        val source = displays.getDisplay(settings.sourceDisplay)
        val output = if (settings.outputDisplay == -1) {
            displays.displays.firstOrNull { it.displayId != settings.sourceDisplay }
        } else displays.getDisplay(settings.outputDisplay)
        val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            getSystemService(PowerManager::class.java).isInteractive
        if (!settings.enabled || !unlocked || source?.state != Display.STATE_ON) {
            foregroundGame = null
            stopSession(if (settings.enabled) message(R.string.waiting_game) else message(R.string.paused))
            return
        }
        if (output == null || output.displayId == source.displayId || output.state != Display.STATE_ON) {
            stopSession(message(R.string.lower_missing))
            return
        }
        val name = topPackage(source.displayId)?.takeIf(::isGame)
        if (name == null) { foregroundGame = null; stopSession(message(R.string.waiting_game)); return }
        if (foregroundGame != name) { foregroundGame = name; activationId++ }
        if (activePackage != name) {
            stopSession(message(R.string.starting_game))
            activePackage = name
            gameLabel = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString() }.getOrDefault(name)
            analyzer = TextRecognitionAnalyzer(settings.sourceLanguage)
            val translator = TranslationManager(TranslationConfig(settings.sourceLanguage, settings.targetLanguage, settings.mode, settings.apiKey), messages ?: this)
            engine = translator
            val session = generation
            pipeline = LiveTranslationPipeline(scope, settings.mode == TranslationMode.OFFLINE, translator::translate,
                onResult = { sequence, text, result, seenAt ->
                    if (session == generation) {
                        translationHistory.add(sequence, text, result)
                        if (sequence >= displayedSequence) {
                            displayedSequence = sequence
                            _state.value = ServiceState(true, gameLabel, message(R.string.live), text, result, activationId,
                                SystemClock.elapsedRealtime() - seenAt)
                        }
                        renderPanel()
                    }
                },
                onFailure = { sequence, error ->
                    if (session == generation) {
                        _state.value = _state.value.copy(status = error.message ?: message(R.string.translation_failed))
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(1500)
                            if (session == generation && pipeline?.isLatest(sequence) == true) {
                                pipeline?.retry(sequence)
                                tracker.retry()
                            }
                        }
                    }
                })
            _state.value = ServiceState(true, gameLabel, message(R.string.waiting_text), activationId = activationId)
        }
        // Keep settings usable on the lower screen without stopping live translation.
        if (topPackage(output.displayId) == packageName) {
            hidePanel()
        } else {
            if (panel == null || panelDisplay != output.displayId) {
                hidePanel()
                panel = TranslationPanel(this, output, settings.fontSize)
                panelDisplay = output.displayId
                backdrop?.let { panel?.setBackdrop(it) }
            }
            renderPanel()
        }
        if (frameJob?.isActive != true) {
            val session = generation
            val reader = analyzer ?: return
            frameJob = scope.launch {
                while (isActive && session == generation) {
                    val started = SystemClock.elapsedRealtime()
                    var waitMs = captureIntervalMs
                    var palette: Bitmap? = null
                    try {
                        val screenshot = screenshot(source.displayId)
                        palette = if (backdrop == null) Bitmap.createScaledBitmap(screenshot, 16, 9, true) else null
                        val text = reader.read(screenshot, settings.subtitleOnly)
                        ensureActive()
                        if (session != generation) return@launch
                        if (palette != null) {
                            if (text.isNotBlank()) { backdrop = palette; panel?.setBackdrop(palette) }
                        }
                        val confirmed = tracker.accept(text)
                        if (confirmed != null) {
                            empty = false
                            _state.value = _state.value.copy(status =
                                if (settings.mode == TranslationMode.OFFLINE) message(R.string.offline_preparing)
                                else message(R.string.translating))
                        }
                        pipeline?.observe(tracker.candidateText, confirmed != null, started)
                        if (tracker.isEmpty && !empty) {
                            empty = true
                            _state.value = _state.value.copy(status = message(R.string.waiting_text))
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: CaptureTooSoon) {
                        captureIntervalMs = (captureIntervalMs + 50).coerceAtMost(1000)
                        waitMs = captureIntervalMs
                    } catch (error: Exception) {
                        if (session == generation) {
                            _state.value = _state.value.copy(status = message(R.string.screen_error, error.message ?: error.javaClass.simpleName))
                            waitMs = 2500
                        }
                    }
                    finally { if (palette !== backdrop) palette?.recycle() }
                    // Independent of the foreground-app poll: no extra 200 ms timer quantization.
                    delay((waitMs - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1))
                }
            }
        }
    }

    private class CaptureTooSoon : Exception()

    private suspend fun screenshot(displayId: Int): Bitmap = suspendCancellableCoroutine { continuation ->
        takeScreenshot(displayId, screenshotExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                var bitmap: Bitmap? = null
                try {
                    if (continuation.isActive) {
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?: error(message(R.string.capture_failed))
                        try { bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false) }
                        finally { wrapped.recycle() }
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally { buffer.close() }
                bitmap?.let { captured ->
                    if (continuation.isActive) continuation.resume(captured) { _, value, _ -> value.recycle() }
                    else captured.recycle()
                }
            }
            override fun onFailure(errorCode: Int) {
                if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    if (continuation.isActive) continuation.resumeWithException(CaptureTooSoon())
                    return
                }
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                    when (errorCode) {
                        ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> message(R.string.access_required)
                        ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> message(R.string.screen_changed)
                        else -> message(R.string.protected_error, errorCode)
                    }
                ))
            }
        })
    }

    private fun renderPanel() { panel?.render(translationHistory.texts) }
    private fun hidePanel() {
        panel?.let { runCatching { it.close() } }
        panel = null
        panelDisplay = -1
    }
    private fun stopSession(message: String) {
        if (activePackage != null) {
            generation++
            frameJob?.cancel()
            pipeline?.close(); pipeline = null
            retryJob?.cancel(); retryJob = null
            // A resource cleanup failure must not disconnect Android's accessibility binding.
            runCatching { analyzer?.close() }
            runCatching { engine?.close() }
            analyzer = null
            engine = null
            activePackage = null
            tracker = DialogueTracker()
            empty = false
        }
        hidePanel()
        backdrop = null
        translationHistory.clear()
        displayedSequence = 0L
        _state.value = ServiceState(connected = true, status = message, activationId = activationId)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        messages = AppLanguage.wrap(this)
        if (::settings.isInitialized) stopSession(message(R.string.settings_updated))
    }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "ui_language") messages = AppLanguage.wrap(this)
        if (key == "thor_font_size") { hidePanel(); return }
        appEligibility.clear()
        stopSession(message(R.string.settings_updated))
    }
    override fun onDestroy() {
        if (::settings.isInitialized) settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
        stopSession(message(R.string.service_closed))
        scope.cancel()
        _state.value = ServiceState()
        super.onDestroy()
    }
    companion object {
        private val _state = MutableStateFlow(ServiceState())
        val state = _state.asStateFlow()
    }
}
