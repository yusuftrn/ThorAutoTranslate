package com.thorautotranslate

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ContextWrapper
import android.view.accessibility.AccessibilityManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsManagerTest {
    @Test fun removedModeAndFilesAreCleanedWithoutDiscardingOtherPreferences() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "retired-model-test").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("cleanup-test-$name", mode)
            override fun deleteSharedPreferences(name: String) = app.deleteSharedPreferences("cleanup-test-$name")
            override fun getExternalFilesDir(type: String?) = File(root, "external").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val settings = SettingsManager(context)
        try {
            settings.prefs.edit().putString("translation_mode", "LOCAL").putString("local_model_id", "retired")
                .putString("google_cloud_api_key", "test-value-not-a-real-key").putString("target_language", "de").commit()
            assertEquals(TranslationMode.OFFLINE, settings.mode)
            listOf(context.getExternalFilesDir(null), context.noBackupFilesDir).forEach {
                File(it, "models/retired.part").apply { parentFile!!.mkdirs(); writeText("temporary") }
            }
            cleanupRetiredModels(context)
            cleanupRetiredModels(context)
            assertEquals(TranslationMode.OFFLINE, settings.mode)
            assertFalse(settings.prefs.contains("local_model_id"))
            assertEquals("test-value-not-a-real-key", settings.apiKey)
            assertEquals("de", settings.targetLanguage)
            assertFalse(File(context.getExternalFilesDir(null), "models").exists())
            assertFalse(File(context.noBackupFilesDir, "models").exists())
        } finally { root.deleteRecursively(); app.deleteSharedPreferences("cleanup-test-app_settings") }
    }
    @Test fun accessibilityServiceDoesNotRequestAShortcutButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = context.getSystemService(AccessibilityManager::class.java).installedAccessibilityServiceList
            .first { it.resolveInfo.serviceInfo.packageName == context.packageName }
        assertEquals(0, service.flags and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON)
    }
}
