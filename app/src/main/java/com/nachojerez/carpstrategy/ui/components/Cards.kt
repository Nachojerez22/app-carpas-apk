package com.nachojerez.carpstrategy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.derived.WaterTemperature
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.AppleLargeTitle
import com.nachojerez.carpstrategy.ui.theme.AppleSectionHeader
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.DesignStyle
import com.nachojerez.carpstrategy.ui.theme.SourceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing

enum class CardBorder { Normal, Dashed, Warning }

/** Radio de las tarjetas según el estilo (Material 16 dp, Apple 14 dp). */
@Composable
fun cardRadius(): Dp = if (CarpTheme.style.style == DesignStyle.Apple) 14.dp else 16.dp

/**
 * Tarjeta base. Material: `surface` con borde `outlineVariant` de 1 dp y sin sombra.
 * Apple: bloque redondeado de 14 dp sin borde. Discontinua para datos estimados o antiguos;
 * borde `warnStrong` de 2 dp para datos con incertidumbre.
 */
@Composable
fun CarpCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: CardBorder = CardBorder.Normal,
    dashedColor: Color = MaterialTheme.colorScheme.outline,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = cardRadius()
    val shape = RoundedCornerShape(radius)
    val borderModifier = when (border) {
        CardBorder.Normal -> if (CarpTheme.style.cardBorder) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier
        CardBorder.Dashed -> Modifier.dashedBorder(dashedColor, radius)
        CardBorder.Warning -> Modifier.border(2.dp, CarpTheme.colors.warnStrong, shape)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .then(borderModifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

/** Título de pantalla. Apple: título grande de 36 sp con una línea en mayúsculas encima. */
@Composable
fun ScreenHeader(title: String, overline: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        if (CarpTheme.style.largeTitle) {
            overline?.let {
                Text(it.uppercase(), style = AppleSectionHeader, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = AppleLargeTitle, color = MaterialTheme.colorScheme.onBackground)
        } else {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            overline?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Cabecera de sección. Apple: MAYÚSCULAS de 13 sp. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.xs)) {
        if (CarpTheme.style.groupedLists) {
            Text(text.uppercase(), style = AppleSectionHeader, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.lg))
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (CarpTheme.style.groupedLists) Modifier.padding(horizontal = Spacing.lg) else Modifier,
            )
        }
    }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Pastilla de texto pequeña (MEDIDA, ESTIMADA, estado de un nivel…). */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, leading: String? = null) {
    Row(
        modifier
            .background(container, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.let { Text(it, color = content, style = MaterialTheme.typography.labelMedium) }
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * Temperatura del agua. MEDIDA: fondo `tertiaryContainer` y cifra exacta. ESTIMADA: borde
 * discontinuo `tertiary`, «≈» y chip «ESTIMADA». Siempre distinguibles (DISENO.md §7).
 */
@Composable
fun WaterTempCard(water: WaterTemperature?, onAddMeasurement: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    if (water == null) {
        CarpCard(modifier, border = CardBorder.Dashed, dashedColor = cs.tertiary) {
            Text(stringResource(R.string.water_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.water_missing), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAddMeasurement) { Text(stringResource(R.string.water_add_measurement)) }
        }
        return
    }
    val measured = water.source == WaterTempSource.MEASURED
    val apple = CarpTheme.style.style == DesignStyle.Apple
    val container = if (measured || apple) cs.tertiaryContainer else cs.surface
    val onContainer = if (measured || apple) cs.onTertiaryContainer else cs.onSurface
    CarpCard(
        modifier,
        containerColor = container,
        border = if (measured) CardBorder.Normal else CardBorder.Dashed,
        dashedColor = cs.tertiary,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.water_title), style = MaterialTheme.typography.titleMedium, color = onContainer, modifier = Modifier.weight(1f))
            if (measured) {
                Pill(stringResource(R.string.water_chip_measured), cs.tertiary, cs.onTertiary, leading = "✓")
            } else {
                Pill(stringResource(R.string.water_chip_estimated), cs.surfaceVariant, cs.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (!measured) Text("≈", style = MaterialTheme.typography.displayMedium, color = onContainer)
            Text(
                Formatting.number(water.valueC, if (measured) 1 else 0),
                style = if (apple) MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Light) else MaterialTheme.typography.displayLarge,
                color = onContainer,
            )
            Text("°C", style = MaterialTheme.typography.titleLarge, color = onContainer, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            SourceBadge(if (measured) SourceKind.Manual else SourceKind.Forecast)
        }
        Text(
            if (measured) {
                stringResource(R.string.water_measured_at, Formatting.hour(water.measuredAt!!))
            } else {
                stringResource(R.string.water_estimated_how)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
        )
        water.trend3dC?.let {
            Text(
                stringResource(if (measured) R.string.water_trend else R.string.water_trend_estimated, Formatting.signed(it)),
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
        }
        if (water.calibrationSamples > 0) {
            Caption(stringResource(R.string.water_calibrated, water.calibrationSamples), color = onContainer)
        }
    }
}

/** Dato con su fuente. Con [warning] (p. ej. discrepancia de viento), borde de incertidumbre. */
@Composable
fun DataCard(
    label: String,
    value: String,
    unit: String,
    source: SourceKind?,
    footnote: String?,
    modifier: Modifier = Modifier,
    warning: String? = null,
    secondary: String? = null,
) {
    CarpCard(modifier, border = if (warning != null) CardBorder.Warning else CardBorder.Normal, contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            source?.let { SourceBadge(it) }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        footnote?.let { Caption(it) }
        warning?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = CarpTheme.colors.warnStrong) }
    }
}

enum class ChainStatus { Passed, Limits, Reduces, Neutral, Blocks }

/** Paso de la cadena de filtros: número, título, estado y porqué. El que limita va resaltado. */
@Composable
fun FilterChainStep(number: Int, title: String, status: ChainStatus, why: String?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val limiting = status == ChainStatus.Limits || status == ChainStatus.Blocks
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (limiting) cs.secondaryContainer else Color.Transparent)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(32.dp).background(if (limiting) cs.secondary else cs.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = if (limiting) cs.onSecondary else cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (limiting) cs.onSecondaryContainer else cs.onSurface)
            why?.let { Caption(it, color = if (limiting) cs.onSecondaryContainer else cs.onSurfaceVariant) }
        }
        val (text, bg, fg) = when (status) {
            ChainStatus.Passed -> Triple(stringResource(R.string.chain_passed), cs.primaryContainer, cs.onPrimaryContainer)
            ChainStatus.Limits -> Triple(stringResource(R.string.chain_limits), cs.secondary, cs.onSecondary)
            ChainStatus.Reduces -> Triple(stringResource(R.string.chain_reduces), CarpTheme.colors.warnContainer, CarpTheme.colors.onWarnContainer)
            ChainStatus.Neutral -> Triple(stringResource(R.string.chain_neutral), cs.surfaceVariant, cs.onSurfaceVariant)
            ChainStatus.Blocks -> Triple(stringResource(R.string.chain_blocks), cs.error, cs.onError)
        }
        Pill(text, bg, fg)
    }
}

/** Demanda alimentaria: 5 barras crecientes (Muy baja → Muy alta), nunca en gramos. */
@Composable
fun DemandMeter(demand: FeedingDemand?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val level = when (demand) {
        FeedingDemand.VERY_LOW -> 0
        FeedingDemand.LOW -> 1
        FeedingDemand.MEDIUM -> 2
        FeedingDemand.HIGH -> 3
        FeedingDemand.VERY_HIGH, FeedingDemand.UNCERTAIN_HEAT -> 4
        null -> -1
    }
    val labels = listOf(R.string.demand_bar_1, R.string.demand_bar_2, R.string.demand_bar_3, R.string.demand_bar_4, R.string.demand_bar_5)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((14 + i * 10).dp)
                        .background(
                            when {
                                i > level -> cs.surfaceContainerHigh
                                demand == FeedingDemand.UNCERTAIN_HEAT -> CarpTheme.colors.warnStrong
                                else -> cs.primary
                            },
                            RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == level) cs.onSurface else cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Fila de prioridad de fuentes: posición, insignia, nombre, subir/bajar e interruptor. */
@Composable
fun SourcePriorityRow(
    position: Int?,
    source: SourceKind,
    name: String,
    description: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canToggle: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = position != null
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            position?.let { "$it" } ?: "–",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp),
        )
        SourceBadge(source, size = 24)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Caption(description)
        }
        if (enabled) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.action_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.action_move_down))
            }
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() }, enabled = canToggle)
    }
}
