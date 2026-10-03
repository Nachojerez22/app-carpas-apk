package com.nachojerez.carpstrategy.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores semánticos FIJOS. No cambian con el color dinámico ni con el estilo
 * (Material / Apple): su significado debe ser siempre el mismo.
 */

enum class EvidenceKind { Strong, Moderate, NoEvidence, LocalHypothesis, IndividualVariability }
enum class SourceKind { Manual, Aemet, Forecast }

@Immutable
data class BadgeColors(val container: Color, val content: Color, val mark: Color)

@Immutable
data class CarpColors(
    val evidence: Map<EvidenceKind, BadgeColors>,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val warnStrong: Color,          // borde de tarjeta con incertidumbre
    val divergenceBand: Color,      // banda entre modelos (gráfica agua/presión)
    val divergenceBandWarm: Color,  // banda en gráfica de aire
    val chartGrid: Color,
    val nightBase: Color,           // horas no legales (rayado)
    val nightStripe: Color,
)

val LightCarpColors = CarpColors(
    evidence = mapOf(
        EvidenceKind.Strong to BadgeColors(Color(0xFFDDEFD5), Color(0xFF1B4D1F), Color(0xFF2E7D32)),
        EvidenceKind.Moderate to BadgeColors(Color(0xFFFBEDC0), Color(0xFF5A4200), Color(0xFFA67C00)),
        EvidenceKind.NoEvidence to BadgeColors(Color(0xFFF8DCD9), Color(0xFF7A1A14), Color(0xFFC62828)),
        EvidenceKind.LocalHypothesis to BadgeColors(Color(0xFFECDDF7), Color(0xFF4A2470), Color(0xFF7B4BB0)),
        EvidenceKind.IndividualVariability to BadgeColors(Color(0xFFD8E6F7), Color(0xFF123F73), Color(0xFF1E63B5)),
    ),
    warnContainer = Color(0xFFFFE39A),
    onWarnContainer = Color(0xFF3A2A00),
    warnStrong = Color(0xFF9A7000),
    divergenceBand = Color(0x331A5E7A),
    divergenceBandWarm = Color(0x2E8A4A27),
    chartGrid = Color(0xFFD5DACB),
    nightBase = Color(0xFFD3D8CA),
    nightStripe = Color(0xFFB4BBA9),
)

val DarkCarpColors = CarpColors(
    evidence = mapOf(
        EvidenceKind.Strong to BadgeColors(Color(0xFF1F3A1F), Color(0xFFBDE7B4), Color(0xFF7BC67E)),
        EvidenceKind.Moderate to BadgeColors(Color(0xFF3E3210), Color(0xFFF5DC8A), Color(0xFFE6B93A)),
        EvidenceKind.NoEvidence to BadgeColors(Color(0xFF451B18), Color(0xFFF6C1BC), Color(0xFFEF7B73)),
        EvidenceKind.LocalHypothesis to BadgeColors(Color(0xFF352548), Color(0xFFE1CDF5), Color(0xFFB992E3)),
        EvidenceKind.IndividualVariability to BadgeColors(Color(0xFF172F4A), Color(0xFFC3DBF7), Color(0xFF6FA8E8)),
    ),
    warnContainer = Color(0xFF4F3B00),
    onWarnContainer = Color(0xFFFFE39A),
    warnStrong = Color(0xFFF1C24B),
    divergenceBand = Color(0x3D8ED0EE),
    divergenceBandWarm = Color(0x38F2B591),
    chartGrid = Color(0xFF2F372B),
    nightBase = Color(0xFF262D23),
    nightStripe = Color(0xFF3A4435),
)

val LocalCarpColors = staticCompositionLocalOf { LightCarpColors }
