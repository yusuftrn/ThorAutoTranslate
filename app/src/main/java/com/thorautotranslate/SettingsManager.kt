package com.thorautotranslate

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context, name: String = "app_settings") {
    val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false) && prefs.getBoolean("screen_consent", false)
        set(value) { prefs.edit().putBoolean("onboarding_complete", value).putBoolean("screen_consent", value).apply() }
    var enabled: Boolean
        get() = prefs.getBoolean("auto_enabled", true)
        set(value) { prefs.edit().putBoolean("auto_enabled", value).apply() }
    var sourceLanguage: String
        get() = prefs.getString("source_language", if (prefs.getBoolean("is_tr_to_en", false)) "tr" else "en") ?: "en"
        set(value) { prefs.edit().putString("source_language", value).apply() }
    var targetLanguage: String
        get() = prefs.getString("target_language", if (prefs.getBoolean("is_tr_to_en", false)) "en" else "tr") ?: "tr"
        set(value) { prefs.edit().putString("target_language", value).apply() }
    var mode: TranslationMode
        get() = runCatching { TranslationMode.valueOf(prefs.getString("translation_mode", "OFFLINE")!!) }.getOrDefault(TranslationMode.OFFLINE)
        set(value) { prefs.edit().putString("translation_mode", value.name).apply() }
    var apiKey: String
        get() = prefs.getString("google_cloud_api_key", "") ?: ""
        set(value) { prefs.edit().putString("google_cloud_api_key", value.trim()).apply() }
    var sourceDisplay: Int
        get() = prefs.getInt("source_display", 0)
        set(value) { prefs.edit().putInt("source_display", value).apply() }
    var outputDisplay: Int
        get() = prefs.getInt("output_display", -1)
        set(value) { prefs.edit().putInt("output_display", value).apply() }
    var subtitleOnly: Boolean
        get() = prefs.getBoolean("subtitle_only", false)
        set(value) { prefs.edit().putBoolean("subtitle_only", value).apply() }
    var fontSize: Float
        get() = prefs.getFloat("thor_font_size", 24f).coerceIn(16f, 42f)
        set(value) { prefs.edit().putFloat("thor_font_size", value.coerceIn(16f, 42f)).apply() }
    fun isAppEnabled(packageName: String, isGame: Boolean): Boolean =
        prefs.getBoolean("app_$packageName", GamePolicy.isAutomatic(packageName, isGame))
    fun setAppEnabled(packageName: String, enabled: Boolean) {
        prefs.edit().putBoolean("app_$packageName", enabled).apply()
    }
}
