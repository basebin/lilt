package com.lilt.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LiltColors {
    val Background = Color(0xFFF7F9FB)
    val Field = Color(0xFFF1F4F7)
    val Ink = Color(0xFF132238)
    val Muted = Color(0xFF697789)
    val Teal = Color(0xFF2BB3A3)
    val Gold = Color(0xFFF2C94C)
    val Line = Color(0xFFE6EBF0)
}

@Composable
fun LiltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LiltColors.Teal,
            secondary = LiltColors.Gold,
            background = LiltColors.Background,
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = LiltColors.Ink,
            onBackground = LiltColors.Ink,
            onSurface = LiltColors.Ink,
        ),
        content = content,
    )
}
