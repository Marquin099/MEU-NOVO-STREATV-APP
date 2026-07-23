package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = RedPrimary,
    secondary = RedDark,
    tertiary = GlowGreen,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = LightText,
    onSecondary = LightText,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = GrayText
  )

private val LightColorScheme = DarkColorScheme // Keep it consistently cinematic and dark!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for cinematic vibe
  dynamicColor: Boolean = false, // Disable dynamic colors to keep professional styling intact
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
