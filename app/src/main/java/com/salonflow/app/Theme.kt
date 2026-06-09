package com.salonflow.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Brand,
    secondary = Accent,
    background = Paper,
    surface = Color.White,
    error = Danger,
)

private val DarkColorScheme = darkColorScheme(
    primary = Brand,
    secondary = Accent,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Danger,
)

@Composable
fun SalonFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
