package com.example.workpilotmini.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = BrandGradientStart,
    onPrimary = Color.White,
    secondary = AccentViolet,
    tertiary = AccentOrange,
    background = DashboardBg,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F2F8),
    onBackground = Color(0xFF1B1D28),
    onSurface = Color(0xFF1B1D28)
)

@Composable
fun WorkPilotMiniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color pulls colors from the user's wallpaper on Android 12+, which fights
    // with the app's own brand palette (the blue/purple gradient, accent tile colors).
    // Off by default so the redesigned dashboard renders consistently on every device.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}