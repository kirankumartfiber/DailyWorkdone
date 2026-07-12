package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = TealPrimary,
    secondary = TealSecondary,
    tertiary = TealTertiary,
    background = TealDarkBackground,
    surface = TealSurface,
    onPrimary = TealOnPrimary,
    onBackground = TealTextPrimary,
    onSurface = TealTextPrimary,
    surfaceVariant = TealSurfaceVariant,
    onSurfaceVariant = TealTextSecondary,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TealSecondary,
    secondary = TealSurfaceVariant,
    tertiary = TealTertiary,
    background = Color(0xFFF0FDF4),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF0C1312),
    onSurface = Color(0xFF0C1312)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      (dynamicColor && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
