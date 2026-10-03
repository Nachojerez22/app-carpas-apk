package com.nachojerez.carpstrategy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nachojerez.carpstrategy.R

/**
 * Atkinson Hyperlegible Next (SIL OFL). Empaquetada en res/font para que funcione
 * sin conexión en el embalse. Descarga: fonts.google.com/specimen/Atkinson+Hyperlegible+Next
 * Archivos esperados en res/font: atkinson_next_regular.ttf, _medium, _semibold, _bold, _extrabold
 *
 * El estilo Apple usa la misma familia: SF Pro no puede distribuirse en Android.
 */
val Atkinson = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_medium, FontWeight.Medium),
    Font(R.font.atkinson_next_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_next_bold, FontWeight.Bold),
    Font(R.font.atkinson_next_extrabold, FontWeight.ExtraBold),
)

private const val TNUM = "tnum" // cifras tabulares en todos los datos

private fun s(size: Int, line: Int, weight: FontWeight, letter: Float = 0f) = TextStyle(
    fontFamily = Atkinson, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = letter.sp, fontFeatureSettings = TNUM,
)

/** Cuerpo +1 sp sobre M3 por lectura al aire libre. */
val CarpTypography = Typography(
    displayLarge = s(64, 64, FontWeight.ExtraBold, -1.5f),   // temp. del agua
    displayMedium = s(44, 48, FontWeight.ExtraBold, -0.5f),
    headlineLarge = s(30, 36, FontWeight.ExtraBold),          // título de pantalla / valoración
    headlineMedium = s(36, 40, FontWeight.ExtraBold),         // valor en DataCard
    headlineSmall = s(24, 30, FontWeight.ExtraBold),
    titleLarge = s(22, 28, FontWeight.ExtraBold),
    titleMedium = s(18, 24, FontWeight.ExtraBold),            // título de tarjeta
    titleSmall = s(16, 22, FontWeight.Bold),
    bodyLarge = s(17, 24, FontWeight.Normal),
    bodyMedium = s(15, 22, FontWeight.Normal),
    bodySmall = s(13, 18, FontWeight.Normal),
    labelLarge = s(15, 20, FontWeight.ExtraBold),             // botones
    labelMedium = s(13, 18, FontWeight.Bold),
    labelSmall = s(12, 16, FontWeight.Bold),                  // insignias (12.5 en diseño)
)

/** Ajustes del estilo Apple: título grande 36, cabeceras de sección en mayúsculas 13. */
val AppleLargeTitle = s(36, 40, FontWeight.ExtraBold, 0.3f)
val AppleSectionHeader = s(13, 18, FontWeight.SemiBold, 0.3f)
