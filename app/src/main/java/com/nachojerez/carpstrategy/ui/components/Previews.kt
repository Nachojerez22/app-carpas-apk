package com.nachojerez.carpstrategy.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.derived.WaterTemperature
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.DesignStyle
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.SourceKind
import com.nachojerez.carpstrategy.ui.theme.ThemeMode
import com.nachojerez.carpstrategy.ui.theme.ThemePrefs
import java.time.Instant
import java.time.LocalDate

/** Claro y oscuro; cada vista previa pinta el estilo Material y el Apple (4 variantes). */
@Preview(name = "Claro", showBackground = true, widthDp = 720)
@Preview(name = "Oscuro", showBackground = true, widthDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class CarpPreviews

@Composable
fun CarpPreviewBox(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DesignStyle.entries.forEach { style ->
            CarpTheme(ThemePrefs(style = style, mode = ThemeMode.System)) {
                Column(
                    Modifier.weight(1f).background(MaterialTheme.colorScheme.background).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { content() }
            }
        }
    }
}

private val previewNow = Instant.parse("2026-10-03T07:37:00Z")

@CarpPreviews
@Composable
private fun BadgesPreview() = CarpPreviewBox {
    EvidenceKind.entries.forEach { EvidenceBadge(it) }
    RegulationBadge()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SourceKind.entries.forEach { SourceBadge(it) } }
}

@CarpPreviews
@Composable
private fun WaterTempCardPreview() = CarpPreviewBox {
    WaterTempCard(WaterTemperature(18.1, WaterTempSource.MEASURED, measuredAt = previewNow, trend3dC = -0.6), onAddMeasurement = {})
    WaterTempCard(WaterTemperature(18.0, WaterTempSource.ESTIMATED, trend3dC = -0.6), onAddMeasurement = {})
}

@CarpPreviews
@Composable
private fun DataCardPreview() = CarpPreviewBox {
    DataCard("Aire", "23", "°C", SourceKind.Aemet, "Máx 26 · mín 13")
    DataCard("Viento dominante", "NO 14", "km/h", SourceKind.Forecast, "3 días seguidos", warning = "Modelos: 8–22 km/h")
}

@CarpPreviews
@Composable
private fun LegalWindowBarPreview() = CarpPreviewBox {
    val sun = SolarCalculator.sunTimes(LocalDate.parse("2026-10-03"), DefaultLocation.value.point)
    LegalWindowBar(LegalWindow.of(sun), sun, previewNow)
}

@CarpPreviews
@Composable
private fun ChainAndDemandPreview() = CarpPreviewBox {
    FilterChainStep(0, "Legalidad", ChainStatus.Passed, "Dentro del horario legal")
    FilterChainStep(2, "Temperatura del agua", ChainStatus.Limits, "Agua a 18 °C bajando")
    FilterChainStep(4, "Capturabilidad", ChainStatus.Neutral, null)
    DemandMeter(FeedingDemand.MEDIUM)
}

@CarpPreviews
@Composable
private fun SourcePriorityPreview() = CarpPreviewBox {
    SourcePriorityRow(1, SourceKind.Manual, "Manual / importado", "Tus medidas", false, true, true, {}, {}, {})
    SourcePriorityRow(2, SourceKind.Aemet, "AEMET", "Observación real", true, true, true, {}, {}, {})
    SourcePriorityRow(null, SourceKind.Forecast, "Modelos", "Media de 3 modelos", false, false, true, {}, {}, {})
}
