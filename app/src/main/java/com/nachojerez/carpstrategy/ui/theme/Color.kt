package com.nachojerez.carpstrategy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Estilo MATERIAL (principal) — verdes de carrizo, arcilla, agua
// ─────────────────────────────────────────────────────────────

val MaterialLightColors = lightColorScheme(
    primary = Color(0xFF36652A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9E7B2), onPrimaryContainer = Color(0xFF0D2004),
    secondary = Color(0xFF8A4A27), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7D9C5), onSecondaryContainer = Color(0xFF331100),
    tertiary = Color(0xFF1A5E7A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E6F5), onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF2F4EC), onBackground = Color(0xFF191D16),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF191D16),
    surfaceVariant = Color(0xFFE8ECE0), onSurfaceVariant = Color(0xFF42493D),
    surfaceContainer = Color(0xFFE3E8DA),        // barra de navegación inferior
    surfaceContainerHigh = Color(0xFFDCE0D4),    // esqueletos de carga, pistas
    outline = Color(0xFF72796B), outlineVariant = Color(0xFFC6CBBD),
)

val MaterialDarkColors = darkColorScheme(
    primary = Color(0xFFA6D58C), onPrimary = Color(0xFF123806),
    primaryContainer = Color(0xFF285018), onPrimaryContainer = Color(0xFFC9EFB0),
    secondary = Color(0xFFF2B591), onSecondary = Color(0xFF4E2209),
    secondaryContainer = Color(0xFF6B3416), onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFF8ED0EE), onTertiary = Color(0xFF003546),
    tertiaryContainer = Color(0xFF004D66), onTertiaryContainer = Color(0xFFC2E8F8),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF10140E), onBackground = Color(0xFFE2E6DA),
    surface = Color(0xFF192017), onSurface = Color(0xFFE2E6DA),
    surfaceVariant = Color(0xFF222A1F), onSurfaceVariant = Color(0xFFC3C8BA),
    surfaceContainer = Color(0xFF1E251B),
    surfaceContainerHigh = Color(0xFF2D3529),
    outline = Color(0xFF8D9385), outlineVariant = Color(0xFF3E463A),
)

// ─────────────────────────────────────────────────────────────
// Estilo APPLE (alternativo) — fondos agrupados, acento verde
// ─────────────────────────────────────────────────────────────

val AppleLightColors = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F1E1), onPrimaryContainer = Color(0xFF1B4D1F),
    secondary = Color(0xFF9A4A22), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8E6DA), onSecondaryContainer = Color(0xFF3A1606),
    tertiary = Color(0xFF0B6488), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF0B6488), onTertiaryContainer = Color(0xFFFFFFFF), // tarjeta de agua sólida
    error = Color(0xFFC62828), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE7E5), onErrorContainer = Color(0xFF5F1412),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF5C5C63),
    surfaceContainer = Color(0xE0F9F9F9),        // barra de pestañas translúcida
    surfaceContainerHigh = Color(0xFFE5E5EA),
    outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFC6C6C8),
)

val AppleDarkColors = darkColorScheme(
    primary = Color(0xFF7BD17F), onPrimary = Color(0xFF00290A),
    primaryContainer = Color(0xFF1B3A1D), onPrimaryContainer = Color(0xFFC9EFB0),
    secondary = Color(0xFFF0A57A), onSecondary = Color(0xFF3D1A06),
    secondaryContainer = Color(0xFF3D2316), onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFF64C8F0), onTertiary = Color(0xFF00303F),
    tertiaryContainer = Color(0xFF0E4A63), onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFFF8A80), onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF3B1513), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000), onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceContainer = Color(0xE0161618),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    outline = Color(0xFF8E8E93), outlineVariant = Color(0xFF38383A),
)
