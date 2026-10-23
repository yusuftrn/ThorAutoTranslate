package com.thorautotranslate

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.app.UiAutomation
import android.provider.Settings
import android.os.ParcelFileDescriptor
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTranslationTest {
    @Test fun readsUppercaseDirectlyFromBitmap() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 500, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLACK)
            drawText("THE GATE IS LOCKED.", 40f, 300f, Paint().apply { color = Color.WHITE; textSize = 64f; isAntiAlias = true })
        }
        val reader = TextRecognitionAnalyzer("en")
        try { assertTrue(reader.read(bitmap, false).contains("THE GATE IS LOCKED")) }
        finally { reader.close() }
    }

    @Test fun readsRealGameTutorial() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        org.junit.Assume.assumeTrue("Optional private calibration screenshot", context.assets.list("").orEmpty().contains("game-tutorial.png"))
        fun frame() = context.assets.open("game-tutorial.png").use { BitmapFactory.decodeStream(it) }
        val reader = TextRecognitionAnalyzer("en")
        try {
            var text = reader.read(frame(), false)
            repeat(2) { if (!text.contains("horse", true)) text = reader.read(frame(), false) }
            assertTrue("Tutorial was missed: $text", text.contains("horse", true))
            assertFalse("FPS leaked into dialogue: $text", text.contains("FPS", true))
        } finally { reader.close() }
    }

    @Test fun unchangedFramesReuseOcrButChangedTextAndCropAreReadAgain() = runBlocking {
        fun frame(text: String) = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.BLACK)
                drawText(text, 40f, 450f, Paint().apply { color = Color.WHITE; textSize = 60f; isAntiAlias = true })
            }
        }
        val reader = TextRecognitionAnalyzer("en")
        try {
            val first = reader.read(frame("The gate is locked."), false)
            val passes = reader.ocrPasses
            repeat(5) { assertEquals(first, reader.read(frame("The gate is locked."), false)) }
            assertEquals("Static frames must not rerun OCR", passes, reader.ocrPasses)
            assertTrue(reader.read(frame("Find Bonnie at the station."), false).contains("Bonnie"))
            assertTrue(reader.ocrPasses > passes)
            val changed = reader.ocrPasses
            assertTrue(reader.read(frame("Find Bonnie at the station."), true).contains("Bonnie"))
            assertTrue("Changing the crop must invalidate OCR", reader.ocrPasses > changed)
        } finally { reader.close() }
    }


    @Test fun animatedBackgroundOutsideDialogueDoesNotTriggerRepeatedOcr() = runBlocking {
        fun frame(color: Int) = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.BLACK)
                drawRect(0f, 0f, 1200f, 180f, Paint().apply { this.color = color })
                drawText("The gate is locked.", 40f, 450f, Paint().apply { this.color = Color.WHITE; textSize = 60f; isAntiAlias = true })
            }
        }
        val reader = TextRecognitionAnalyzer("en")
        try {
            assertTrue(reader.read(frame(Color.RED), false).contains("locked"))
            val before = reader.ocrPasses
            assertTrue(reader.read(frame(Color.BLUE), false).contains("locked"))
            assertEquals("Only the background changed", before, reader.ocrPasses)
        } finally { reader.close() }
    }

    @Test fun fullScreenDiscoveryFindsTextOutsideThePreviousDialogueRegion() = runBlocking {
        fun frame(extra: Boolean) = Bitmap.createBitmap(1200, 700, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.BLACK)
                val paint = Paint().apply { color = Color.WHITE; textSize = 52f; isAntiAlias = true }
                if (extra) drawText("Find Bonnie at the station.", 40f, 150f, paint)
                drawText("The gate is locked.", 40f, 550f, paint)
            }
        }
        val reader = TextRecognitionAnalyzer("en")
        try {
            reader.read(frame(false), false)
            SystemClock.sleep(1100)
            assertTrue(reader.read(frame(true), false).contains("Bonnie"))
        } finally { reader.close() }
    }

    @Test fun emptyScreensNeverSpendFourOcrPassesOnOneFrame() = runBlocking {
        val reader = TextRecognitionAnalyzer("en")
        try {
            repeat(3) {
                val before = reader.ocrPasses
                val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
                assertEquals("", reader.read(bitmap, false))
                assertTrue("OCR attempts must be bounded", reader.ocrPasses - before <= 2)
            }
        } finally { reader.close() }
    }

    /** Requires the accessibility service to be enabled on a two-display test device. */
    @Test fun translatesUpperDisplayShowsThreeHistoryRowsAndStopsWhenGameLeaves() = exerciseSession()

    @Test fun offlineTranslationUsesTheSameAutomaticLifecycleAndFullHistory() = exerciseSession(TranslationMode.OFFLINE)

    private fun exerciseSession(method: TranslationMode = TranslationMode.FREE) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val settings = SettingsManager(context)
        val original = settings.prefs.all
        val originalLocales = if (android.os.Build.VERSION.SDK_INT >= 33)
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales else null
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun hasLowerPanel(): Boolean {
            val windows = automation.windowsOnAllDisplays
            return (0 until windows.size()).any { index -> windows.keyAt(index) != 0 &&
                windows.valueAt(index).any { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY } }
        }
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = "${context.packageName}/${GameTranslationService::class.java.name}"
        fun setServices(value: String) {
            require(value.matches(Regex("[A-Za-z0-9_./:$]*")))
            val command = if (value.isBlank()) "settings delete secure enabled_accessibility_services"
                else "settings put secure enabled_accessibility_services $value"
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        try {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
            val others = services.split(':').filter { it.isNotBlank() && it != component }.joinToString(":")
            setServices(others)
            SystemClock.sleep(500)
            setServices(listOf(others, component).filter { it.isNotBlank() }.joinToString(":"))
            settings.onboardingComplete = true
            AppLanguage.select(context, "tr")
            settings.enabled = true
            settings.sourceDisplay = 0
            settings.outputDisplay = -1
            settings.sourceLanguage = "en"
            settings.targetLanguage = "tr"
            settings.mode = method
            settings.subtitleOnly = false
            settings.setAppEnabled(testContext.packageName, true)
            waitUntil(15000) { GameTranslationService.state.value.connected }
            val lowerId = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .displays.first { it.displayId != 0 }.displayId
            fun namedNode(label: String): android.view.accessibility.AccessibilityNodeInfo? {
                fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
                    for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
                    return null
                }
                return automation.windowsOnAllDisplays[lowerId].orEmpty().firstNotNullOfOrNull { find(it.root) }
            }
            fun hasSettingsActivity() = automation.windowsOnAllDisplays[lowerId].orEmpty().any {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() == context.packageName
            }
            fun launchApp() {
                context.startActivity(Intent(context, MainActivity::class.java).putExtra("lower_screen", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    ActivityOptions.makeBasic().setLaunchDisplayId(lowerId).toBundle())
                SystemClock.sleep(350)
            }
            // Settings already visible when a game starts must yield to automatic translation.
            launchApp()
            waitUntil(5000) { hasSettingsActivity() }
            if (method == TranslationMode.OFFLINE) runBlocking {
                val warmup = TranslationManager(TranslationConfig("en", "tr", method, ""), context)
                try { warmup.translate("Prepare language models.") } finally { warmup.close() }
            }
            fun showLine(text: String, fontSize: Float = 32f) {
                val started = SystemClock.elapsedRealtime()
                context.startActivity(Intent().setComponent(ComponentName(testContext.packageName, ScreenFixtureActivity::class.java.name))
                    .putExtra("line", text).putExtra("font_size", fontSize).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
                waitUntil(30000) {
                    val current = GameTranslationService.state.value
                    dialogueKey(current.original) == dialogueKey(text) && current.translated.isNotBlank()
                }
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("translation_method", method.name)
                    putLong("live_latency_ms", SystemClock.elapsedRealtime() - started)
                    putLong("observed_to_result_ms", GameTranslationService.state.value.latencyMs)
                })
            }
            showLine("The gate is locked.")
            waitUntil(5000) { hasLowerPanel() && !hasSettingsActivity() }
            val state = GameTranslationService.state.value
            assertTrue(state.original.contains("The gate is locked"))
            assertTrue("Expected Turkish translation; got ${state.translated}", state.translated.lowercase().contains("kapı"))
            assertEquals("Canlı", state.status)
            fun panelTexts(): List<String> {
                val windows = automation.windowsOnAllDisplays
                val texts = mutableListOf<String>()
                fun read(node: android.view.accessibility.AccessibilityNodeInfo?) {
                    if (node == null) return
                    node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
                    for (index in 0 until node.childCount) read(node.getChild(index))
                }
                for (index in 0 until windows.size()) if (windows.keyAt(index) != 0) {
                    windows.valueAt(index).filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.forEach { read(it.root) }
                }
                return texts
            }
            listOf("Follow me to the old bridge.", "Press B to jump over the fence.", "Find Bonnie at the station.").forEach { showLine(it) }
            waitUntil(5000) { panelTexts().size == 3 }
            val history = panelTexts()
            assertEquals(3, history.size)
            assertFalse(history.any { it.contains("Canlı çeviri") || it.contains("EN → TR") || it.contains("FPS") })
            SystemClock.sleep(250)
            instrumentation.sendStatus(2, Bundle().apply { putString("snapshot", "history_ready") })
            SystemClock.sleep(2000)
            val lastSource = GameTranslationService.state.value.original
            settings.fontSize = if (settings.fontSize < 40f) settings.fontSize + 2f else 24f
            SystemClock.sleep(600)
            assertEquals("Font changes must keep the current session", lastSource, GameTranslationService.state.value.original)
            waitUntil(5000) { panelTexts() == history }
            // An ordinary app launch during gameplay must return straight to the translation panel.
            launchApp()
            waitUntil(5000) { !hasSettingsActivity() && hasLowerPanel() }
            val gear = namedNode("Ayarlar")!!
            assertTrue(gear.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil(5000) { !hasLowerPanel() && namedNode("Çeviriye dön") != null }
            assertNotNull(namedNode("Canlı"))
            assertNotNull(namedNode("Ayarlar"))
            assertNotNull(namedNode("Uygulamalar"))
            assertNull("Scanning tab must be removed", namedNode("Oyunlar"))
            val activation = GameTranslationService.state.value.activationId
            settings.mode = TranslationMode.OFFLINE
            SystemClock.sleep(600)
            assertEquals("Settings changes must not count as a new game", activation, GameTranslationService.state.value.activationId)
            assertTrue("Explicit settings must remain usable", hasSettingsActivity())
            settings.mode = method
            waitUntil(5000) { namedNode("Çeviriye dön") != null }
            var button = namedNode("Çeviriye dön")!!
            while (!button.isClickable && button.parent != null) button = button.parent
            assertTrue(button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil(5000) { hasLowerPanel() && !hasSettingsActivity() }
            settings.targetLanguage = "en"
            settings.fontSize = 42f
            showLine("Follow me to the old bridge.")
            showLine("Press B to jump over the fence.")
            val longPassage = "Follow the river to the old bridge and wait for the rider. " .repeat(7) + "Meet Bonnie at the wooden gate."
            showLine(longPassage, 22f)
            waitUntil(5000) { panelTexts().size == 3 && panelTexts().last().contains("wooden gate") }
            assertTrue("The full long translation must be retained", panelTexts().last().length > 400)
            SystemClock.sleep(300)
            instrumentation.sendStatus(2, Bundle().apply { putString("snapshot", "long_history_ready") })
            SystemClock.sleep(1200)
            fun scrollingNode(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isScrollable) return node
                for (index in 0 until node.childCount) scrollingNode(node.getChild(index))?.let { return it }
                return null
            }
            val scroller = automation.windowsOnAllDisplays[lowerId].orEmpty()
                .filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.firstNotNullOfOrNull { scrollingNode(it.root) }
            assertNotNull("A long translation must be scrollable", scroller)
            assertTrue(scroller!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            repeat(6) {
                SystemClock.sleep(120)
                scroller.refresh()
                scroller.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            SystemClock.sleep(300)
            instrumentation.sendStatus(2, Bundle().apply { putString("snapshot", "long_history_end") })
            SystemClock.sleep(1200)
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
            waitUntil(5000) { GameTranslationService.state.value.game.isBlank() }
            assertEquals("", GameTranslationService.state.value.translated)
            waitUntil(5000) { !hasLowerPanel() }
        } finally {
            val edit = settings.prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Float -> edit.putFloat(key, value)
                is Long -> edit.putLong(key, value)
            } }
            edit.remove("app_${testContext.packageName}").commit()
            if (android.os.Build.VERSION.SDK_INT >= 33 && originalLocales != null)
                context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = originalLocales
            setServices(services)
        }
    }
    private fun waitUntil(timeout: Long, predicate: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!predicate() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
        assertTrue("Timed out: ${GameTranslationService.state.value}", predicate())
    }
}
