package com.nachojerez.carpstrategy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.SourceKind
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind

// ── Insignia de evidencia ────────────────────────────────────
// Color + FORMA propia (para daltonismo y sol directo):
// Fuerte ●  Moderada ◐  Sin evidencia ⊘  Hipótesis local ◆  Variabilidad ∿

@StringRes
fun EvidenceKind.labelRes(short: Boolean = false): Int = when (this) {
    EvidenceKind.Strong -> R.string.evidence_strong
    EvidenceKind.Moderate -> if (short) R.string.evidence_moderate_short else R.string.evidence_moderate
    EvidenceKind.NoEvidence -> if (short) R.string.evidence_none_short else R.string.evidence_none
    EvidenceKind.LocalHypothesis -> R.string.evidence_local
    EvidenceKind.IndividualVariability -> if (short) R.string.evidence_individual_short else R.string.evidence_individual
}

@Composable
fun EvidenceBadge(evidence: EvidenceKind, modifier: Modifier = Modifier, short: Boolean = false) {
    val c = CarpTheme.colors.evidence.getValue(evidence)
    val description = stringResource(R.string.evidence_description, stringResource(evidence.labelRes()))
    val text = stringResource(evidence.labelRes(short))
    Row(
        modifier
            .height(24.dp)
            .background(c.container, RoundedCornerShape(12.dp))
            .padding(start = 7.dp, end = 9.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        EvidenceMark(evidence, c.mark)
        Text(text, color = c.content, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EvidenceMark(e: EvidenceKind, color: Color) = Canvas(Modifier.size(12.dp)) {
    val w = size.width; val r = w * 0.38f; val ctr = Offset(w / 2, w / 2)
    val stroke = Stroke(width = w * 0.13f)
    when (e) {
        EvidenceKind.Strong -> drawCircle(color, radius = w * 0.42f, center = ctr)
        EvidenceKind.Moderate -> {
            drawCircle(color, r, ctr, style = stroke)
            drawArc(color, -90f, 180f, useCenter = true, topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2))
        }
        EvidenceKind.NoEvidence -> {
            drawCircle(color, r, ctr, style = stroke)
            drawLine(color, Offset(w * 0.23f, w * 0.77f), Offset(w * 0.77f, w * 0.23f), strokeWidth = stroke.width)
        }
        EvidenceKind.LocalHypothesis -> drawPath(Path().apply {
            moveTo(w / 2, w * 0.05f); lineTo(w * 0.95f, w / 2); lineTo(w / 2, w * 0.95f); lineTo(w * 0.05f, w / 2); close()
        }, color)
        EvidenceKind.IndividualVariability -> drawPath(Path().apply {
            moveTo(w * 0.08f, w * 0.58f)
            cubicTo(w * 0.22f, w * 0.30f, w * 0.36f, w * 0.30f, w * 0.5f, w * 0.58f)
            cubicTo(w * 0.64f, w * 0.86f, w * 0.78f, w * 0.86f, w * 0.92f, w * 0.58f)
        }, color, style = Stroke(width = w * 0.15f))
    }
}

// ── Insignia de fuente ───────────────────────────────────────
// A = relleno (observación real) · M = contorno (tu dato) · P = discontinuo (previsión)

@Composable
fun SourceBadge(source: SourceKind, modifier: Modifier = Modifier, size: Int = 20) {
    val cs = MaterialTheme.colorScheme
    val (letter, descRes) = when (source) {
        SourceKind.Manual -> "M" to R.string.source_badge_manual
        SourceKind.Aemet -> "A" to R.string.source_badge_aemet
        SourceKind.Forecast -> "P" to R.string.source_badge_forecast
    }
    val desc = stringResource(descRes)
    val color = when (source) {
        SourceKind.Manual -> cs.secondary
        SourceKind.Aemet -> cs.tertiary
        SourceKind.Forecast -> cs.onSurfaceVariant
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .defaultMinSize(size.dp, size.dp)
            .semantics { contentDescription = desc }
            .then(
                when (source) {
                    SourceKind.Aemet -> Modifier.background(color, shape)
                    SourceKind.Manual -> Modifier.border(1.5.dp, color, shape)
                    SourceKind.Forecast -> Modifier.dashedBorder(color, 6.dp)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = if (source == SourceKind.Aemet) cs.onTertiary else color,
            fontSize = (size * 0.6f).sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Borde discontinuo: insignia P y tarjeta de agua ESTIMADA. */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        ),
    )
}

// ── Chip de aviso ────────────────────────────────────────────

enum class WarningType { Stale, ModelDivergence, Unverified }

@Composable
fun WarningChip(type: WarningType, text: String, icon: Painter, modifier: Modifier = Modifier) {
    val carp = CarpTheme.colors
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (type) {
        WarningType.Stale, WarningType.ModelDivergence -> carp.warnContainer to carp.onWarnContainer
        WarningType.Unverified -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    Row(
        modifier
            .defaultMinSize(minHeight = 36.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
    }
}
