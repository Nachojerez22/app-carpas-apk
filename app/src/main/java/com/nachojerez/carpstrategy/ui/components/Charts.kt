package com.nachojerez.carpstrategy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.Point
import com.nachojerez.carpstrategy.domain.derived.SunTimes
import com.nachojerez.carpstrategy.domain.rules.TimeWindow
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.today.ChartData
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M")

/**
 * Gráfica de tendencia (DISENO.md §4): observado (línea sólida de 3 dp), «ahora» (línea
 * discontinua y punto), previsión media (discontinua sobre `surfaceVariant`) y banda min–max
 * de los modelos. Serie secundaria opcional (agua) en `tertiary`.
 */
@Composable
fun TrendChart(
    data: ChartData,
    description: String,
    modifier: Modifier = Modifier,
    warmBand: Boolean = false,
    decimals: Int = 0,
) {
    val cs = MaterialTheme.colorScheme
    val carp = CarpTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = cs.onSurfaceVariant)
    val today = stringResource(R.string.chart_today)
    val range = data.valueRange()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = description },
    ) {
        if (range == null) return@Canvas
        val left = 40.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val width = size.width - left
        val total = Duration.between(data.from, data.to).toMillis().toFloat()
        fun x(t: Instant) = left + width * (Duration.between(data.from, t).toMillis() / total)
        fun y(v: Double) = (bottom * (1 - (v - range.start) / (range.endInclusive - range.start))).toFloat()

        // Fondo de la previsión.
        val nowX = x(data.now)
        drawRect(cs.surfaceVariant, topLeft = Offset(nowX, 0f), size = Size(size.width - nowX, bottom))

        // Rejilla horizontal y etiquetas del eje.
        for (i in 0..2) {
            val v = range.start + (range.endInclusive - range.start) * i / 2
            val yy = y(v)
            drawLine(carp.chartGrid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(Formatting.number(v, decimals), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2).coerceIn(0f, bottom - label.size.height)))
        }

        // Etiquetas de día a mediodía local; «Hoy» en el día actual.
        var day = data.from.atZone(Formatting.MADRID).toLocalDate()
        val lastDay = data.to.atZone(Formatting.MADRID).toLocalDate()
        val todayDate = data.now.atZone(Formatting.MADRID).toLocalDate()
        while (!day.isAfter(lastDay)) {
            val noon = day.atTime(12, 0).atZone(Formatting.MADRID).toInstant()
            if (!noon.isBefore(data.from) && !noon.isAfter(data.to)) {
                val text = if (day == todayDate) today else DAY_LABEL.format(day)
                val label = measurer.measure(text, if (day == todayDate) labelStyle.copy(color = cs.onSurface) else labelStyle)
                drawText(label, topLeft = Offset(x(noon) - label.size.width / 2, bottom + 2.dp.toPx()))
            }
            day = day.plusDays(1)
        }

        // Banda de discrepancia entre modelos.
        if (data.bandLow.size >= 2 && data.bandLow.size == data.bandHigh.size) {
            val band = Path().apply {
                data.bandHigh.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.time), y(p.value)) else lineTo(x(p.time), y(p.value)) }
                data.bandLow.asReversed().forEach { p -> lineTo(x(p.time), y(p.value)) }
                close()
            }
            drawPath(band, if (warmBand) carp.divergenceBandWarm else carp.divergenceBand)
        }

        line(data.forecast, ::x, ::y, cs.onSurfaceVariant, 2.dp.toPx(), dashed = true)
        line(data.secondary, ::x, ::y, cs.tertiary, 2.dp.toPx(), dashed = false)
        data.secondary.forEach { drawCircle(cs.tertiary, 3.dp.toPx(), Offset(x(it.time), y(it.value))) }
        line(data.observed, ::x, ::y, cs.primary, 3.dp.toPx(), dashed = false)

        // Ahora.
        drawLine(
            cs.onSurface,
            Offset(nowX, 0f),
            Offset(nowX, bottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
        data.observed.lastOrNull()?.let { drawCircle(cs.primary, 5.dp.toPx(), Offset(x(it.time), y(it.value))) }
    }
}

private fun DrawScope.line(
    points: List<Point>,
    x: (Instant) -> Float,
    y: (Double) -> Float,
    color: Color,
    width: Float,
    dashed: Boolean,
) {
    if (points.size < 2) return
    val path = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.time), y(p.value)) else lineTo(x(p.time), y(p.value)) }
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
        ),
    )
}

/**
 * Barra de 24 h del día local: horas no legales rayadas, ventana legal en `primary`, franjas
 * recomendadas resaltadas y «ahora» como marca vertical. Nunca se recomiendan horas nocturnas.
 */
@Composable
fun LegalWindowBar(
    legal: LegalWindow?,
    sun: SunTimes,
    now: Instant,
    modifier: Modifier = Modifier,
    highlights: List<TimeWindow> = emptyList(),
) {
    val cs = MaterialTheme.colorScheme
    val carp = CarpTheme.colors
    val dayStart = sun.date.atStartOfDay(Formatting.MADRID).toInstant()
    val dayEnd = sun.date.plusDays(1).atStartOfDay(Formatting.MADRID).toInstant()
    val total = Duration.between(dayStart, dayEnd).toMillis().toFloat()
    fun fraction(t: Instant) = (Duration.between(dayStart, t).toMillis() / total).coerceIn(0f, 1f)
    val description = legal?.let {
        stringResource(R.string.legal_bar_description, Formatting.clock(it.start), Formatting.clock(it.end))
    } ?: stringResource(R.string.derived_legal_unknown)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = description },
        ) {
            val h = size.height
            drawRect(carp.nightBase, size = Size(size.width, h))
            clipRect {
                val step = 8.dp.toPx()
                var start = -h
                while (start < size.width) {
                    drawLine(carp.nightStripe, Offset(start, h), Offset(start + h, 0f), strokeWidth = 2.dp.toPx())
                    start += step
                }
            }
            legal?.let {
                val s = fraction(it.start) * size.width
                val e = fraction(it.end) * size.width
                drawRect(cs.primary, topLeft = Offset(s, 0f), size = Size(e - s, h))
            }
            highlights.forEach {
                val s = fraction(it.start) * size.width
                val e = fraction(it.end) * size.width
                drawRect(cs.primaryContainer, topLeft = Offset(s, h * 0.25f), size = Size(e - s, h * 0.5f))
            }
            if (!now.isBefore(dayStart) && now.isBefore(dayEnd)) {
                val nx = fraction(now) * size.width
                drawLine(cs.onSurface, Offset(nx, -2f), Offset(nx, h + 2f), strokeWidth = 3.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption("00")
            Caption("↑ ${Formatting.clock(sun.sunrise)}")
            Caption("↓ ${Formatting.clock(sun.sunset)}")
            Caption("24")
        }
    }
}
