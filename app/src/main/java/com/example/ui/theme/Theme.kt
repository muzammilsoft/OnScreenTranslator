package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BentoDarkColorScheme = darkColorScheme(
    primary = BentoLavender,
    secondary = BentoNeonGreen,
    tertiary = BentoDeepViolet,
    background = BentoBackground,
    surface = BentoCardBg,
    surfaceVariant = BentoAccentCard,
    outline = BentoBorder,
    onPrimary = BentoDeepViolet,
    onSecondary = Color.Black,
    onBackground = BentoTextWhite,
    onSurface = BentoTextWhite,
    onSurfaceVariant = BentoLavender
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = BentoDarkColorScheme, typography = Typography, content = content)
}

