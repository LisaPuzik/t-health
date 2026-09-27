package com.thealth.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра из HTML-прототипа
val Yellow = Color(0xFFFFDD2D)
val YellowPress = Color(0xFFE8C700)
val Ink = Color(0xFF333333)
val GrayBg = Color(0xFFF5F5F5)
val GrayText = Color(0xFF9299A2)

private val Scheme = lightColorScheme(
    primary = Yellow,
    onPrimary = Ink,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = GrayBg,
    onSurfaceVariant = GrayText
)

@Composable
fun THealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
