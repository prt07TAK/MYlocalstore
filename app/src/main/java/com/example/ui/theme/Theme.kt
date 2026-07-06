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
    primary = WarmDarkPrimary,
    secondary = WarmDarkSecondary,
    tertiary = WarmDarkTertiary,
    background = WarmDarkBackground,
    surface = WarmDarkSurface,
    onPrimary = DarkCharcoal,
    onSecondary = DarkCharcoal,
    onTertiary = DarkCharcoal,
    onBackground = SoftCream,
    onSurface = SoftCream
  )

private val LightColorScheme =
  lightColorScheme(
    primary = WarmLightPrimary,
    secondary = WarmLightSecondary,
    tertiary = WarmLightTertiary,
    background = WarmLightBackground,
    surface = WarmLightSurface,
    onPrimary = PureWhite,
    onSecondary = PureWhite,
    onTertiary = DarkCharcoal,
    onBackground = DarkCharcoal,
    onSurface = DarkCharcoal
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
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
