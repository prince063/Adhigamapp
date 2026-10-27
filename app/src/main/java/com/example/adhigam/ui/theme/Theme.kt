package com.example.adhigam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Indigo700,
    onPrimary = Color.White,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo700,
    secondary = Amber600,
    secondaryContainer = Amber200,
    tertiary = Teal600,
    tertiaryContainer = Teal200,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo200,
    secondary = Amber200,
    tertiary = Teal200,
    error = ErrorRed
)

// Dynamic color is intentionally off so the school's brand colors stay consistent on every device.
@Composable
fun AdhigamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
