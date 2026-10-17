package com.wledcontroller.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86B),
    onPrimary = Color(0xFF3A2000),
    primaryContainer = Color(0xFF55330A),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFF9EC9FF),
    tertiary = Color(0xFFD6A8FF),
    background = Color(0xFF0E0F14),
    surface = Color(0xFF0E0F14),
    surfaceVariant = Color(0xFF2A2C35),
    surfaceContainer = Color(0xFF181A21),
    surfaceContainerHigh = Color(0xFF20222A),
    surfaceContainerHighest = Color(0xFF2A2C35),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5100),
    secondary = Color(0xFF2D628F),
    tertiary = Color(0xFF7A4BA6),
)

@Composable
fun WledTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
