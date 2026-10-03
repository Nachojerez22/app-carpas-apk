package com.nachojerez.carpstrategy.ui.theme

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

private val ReedGreen = Color(0xFF2E5E3A)
private val ReedGreenLight = Color(0xFF9CD3A6)
private val Clay = Color(0xFF8A5A2B)
private val ClayLight = Color(0xFFF2B98A)

private val LightColors = lightColorScheme(primary = ReedGreen, secondary = Clay)
private val DarkColors = darkColorScheme(primary = ReedGreenLight, secondary = ClayLight)

@Composable
fun CarpStrategyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
