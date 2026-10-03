package com.nachojerez.carpstrategy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Las 4 variantes: estilo × modo. El modo puede seguir al sistema. */
enum class DesignStyle { Material, Apple }
enum class ThemeMode { System, Light, Dark }

/** Preferencias del usuario (se guardan en la tabla de ajustes de UserDataDatabase). Material es el estilo por defecto. */
data class ThemePrefs(
    val style: DesignStyle = DesignStyle.Material,
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false, // solo aplica a Material en Android 12+
)

@Immutable
data class CarpStyle(
    val style: DesignStyle,
    val groupedLists: Boolean,       // Apple: listas agrupadas en bloque con divisores
    val largeTitle: Boolean,         // Apple: título grande 36 sp
    val translucentNavBar: Boolean,  // Apple: barra inferior con desenfoque
    val cardBorder: Boolean,         // Material: tarjetas sin sombra, borde outlineVariant 1 dp
)

object Spacing {
    val xs: Dp = 4.dp; val sm: Dp = 8.dp; val md: Dp = 12.dp
    val lg: Dp = 16.dp; val xl: Dp = 24.dp; val xxl: Dp = 32.dp
    val screen: Dp = 16.dp; val touchMin: Dp = 48.dp
}

private val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // insignia de fuente
    small = RoundedCornerShape(12.dp),       // chip, campo
    medium = RoundedCornerShape(16.dp),      // tarjeta de dato
    large = RoundedCornerShape(20.dp),       // sección
    extraLarge = RoundedCornerShape(28.dp),  // botón / hoja
)
private val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),       // control segmentado
    medium = RoundedCornerShape(14.dp),      // lista agrupada
    large = RoundedCornerShape(24.dp),       // tarjeta destacada
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalCarpStyle = staticCompositionLocalOf {
    CarpStyle(DesignStyle.Material, groupedLists = false, largeTitle = false, translucentNavBar = false, cardBorder = true)
}

@Composable
fun CarpTheme(prefs: ThemePrefs = ThemePrefs(), content: @Composable () -> Unit) {
    val dark = when (prefs.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val scheme = when {
        prefs.style == DesignStyle.Material && prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        prefs.style == DesignStyle.Material -> if (dark) MaterialDarkColors else MaterialLightColors
        else -> if (dark) AppleDarkColors else AppleLightColors
    }
    val style = when (prefs.style) {
        DesignStyle.Material -> CarpStyle(DesignStyle.Material, false, false, false, true)
        DesignStyle.Apple -> CarpStyle(DesignStyle.Apple, true, true, true, false)
    }
    CompositionLocalProvider(
        LocalCarpColors provides if (dark) DarkCarpColors else LightCarpColors, // fijos, nunca dinámicos
        LocalCarpStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CarpTypography,
            shapes = if (prefs.style == DesignStyle.Apple) AppleShapes else MaterialShapes,
            content = content,
        )
    }
}

/** Acceso: CarpTheme.colors.evidence[EvidenceKind.Strong], CarpTheme.style.groupedLists */
object CarpTheme {
    val colors: CarpColors @Composable @ReadOnlyComposable get() = LocalCarpColors.current
    val style: CarpStyle @Composable @ReadOnlyComposable get() = LocalCarpStyle.current
}
