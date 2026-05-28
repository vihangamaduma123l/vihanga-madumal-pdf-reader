package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccessibleDarkPrimary,
    onPrimary = AccessibleDarkOnPrimary,
    background = AccessibleDarkBackground,
    onBackground = AccessibleDarkOnBackground,
    surface = AccessibleDarkSurface,
    onSurface = AccessibleDarkOnSurface,
    secondary = AccessibleDarkSecondary,
    outline = AccessibleDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = AccessibleLightPrimary,
    onPrimary = AccessibleLightOnPrimary,
    background = AccessibleLightBackground,
    onBackground = AccessibleLightOnBackground,
    surface = AccessibleLightSurface,
    onSurface = AccessibleLightOnSurface,
    secondary = AccessibleLightSecondary,
    outline = AccessibleLightPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
