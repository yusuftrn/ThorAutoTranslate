package com.thorautotranslate

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    val names = linkedMapOf("en" to "English", "tr" to "Türkçe", "de" to "Deutsch", "es" to "Español",
        "fr" to "Français", "pt" to "Português", "ja" to "日本語", "ko" to "한국어", "zh" to "简体中文")

    fun selected(context: Context): String {
        val language = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language
                ?: android.content.res.Resources.getSystem().configuration.locales[0].language
        } else context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("ui_language", null)
            ?: android.content.res.Resources.getSystem().configuration.locales[0].language
        return language.takeIf { it in names } ?: "en"
    }
    fun select(context: Context, language: String) {
        require(language in names)
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags(language)
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString("ui_language", language).apply()
    }
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(selected(context))
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        })
    }
}
