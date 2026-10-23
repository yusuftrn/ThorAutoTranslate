package com.thorautotranslate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ThorAutoTranslateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFA0D2B4), primaryContainer = Color(0xFF244534),
        onPrimary = Color(0xFF123723), secondary = Color(0xFFA0D2B4), background = Color(0xFF0A0D0C),
        surface = Color(0xFF101613), onSurface = Color(0xFFE6EDE8)
    ), typography = Typography, content = content)
}
