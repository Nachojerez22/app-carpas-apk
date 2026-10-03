package com.nachojerez.carpstrategy.ui.conditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.MoonPhaseName
import com.nachojerez.carpstrategy.domain.derived.SeasonByWater
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.derived.WindSummary
import com.nachojerez.carpstrategy.ui.conditions.Formatting.clock
import com.nachojerez.carpstrategy.ui.conditions.Formatting.number
import com.nachojerez.carpstrategy.ui.conditions.Formatting.signed
import java.time.Duration

/** Resumen de los parámetros derivados con su etiqueta de evidencia (CONOCIMIENTO.md §2). */
@Composable
fun DerivedCard(derived: DerivedConditions?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.derived_title), style = MaterialTheme.typography.titleMedium)
            if (derived == null) {
                Text(stringResource(R.string.derived_not_enough))
                return@Column
            }
            LegalLine(derived)
            WaterLines(derived)
            AirLines(derived)
            derived.wind24h?.let { WindLine(stringResource(R.string.derived_wind_24h), it) }
            derived.wind48h?.let { WindLine(stringResource(R.string.derived_wind_48h), it) }
            RainLines(derived)
            ReservoirLine(derived)
            HorizontalDivider()
            WeightZeroLines(derived)
        }
    }
}

@Composable
private fun LegalLine(d: DerivedConditions) {
    val legal = d.legalToday
    if (legal == null) {
        Text(stringResource(R.string.derived_legal_unknown), color = MaterialTheme.colorScheme.error)
    } else {
        Text(
            stringResource(R.string.derived_legal, clock(legal.start), clock(legal.end), clock(d.sunToday.sunrise), clock(d.sunToday.sunset)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun WaterLines(d: DerivedConditions) {
    val water = d.water
    if (water == null) {
        Text(stringResource(R.string.derived_water_missing))
        return
    }
    if (water.source == WaterTempSource.MEASURED) {
        Text(stringResource(R.string.derived_water_measured, number(water.valueC), Formatting.hour(water.measuredAt!!)))
    } else {
        Text(
            stringResource(R.string.derived_water_estimated, number(water.valueC)),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
    if (water.calibrationSamples > 0) {
        Caption(stringResource(R.string.derived_water_calibrated, water.calibrationSamples, signed(water.calibrationOffsetC)))
    }
    water.trend3dC?.let { Caption(stringResource(R.string.derived_water_trend, signed(it))) }
    d.season?.let { s ->
        val name = stringResource(
            when (s.season) {
                SeasonByWater.WINTER -> R.string.season_winter
                SeasonByWater.SPRING -> R.string.season_spring
                SeasonByWater.SUMMER -> R.string.season_summer
                SeasonByWater.AUTUMN -> R.string.season_autumn
            },
        )
        Text(stringResource(R.string.derived_season, if (s.fromCalendar) stringResource(R.string.derived_season_calendar, name) else name))
    }
}

@Composable
private fun AirLines(d: DerivedConditions) {
    d.airTrend?.let {
        Text(stringResource(R.string.derived_air, number(it.last24hMeanC), number(it.previous3DaysMeanC), signed(it.deltaC)))
    }
    val bias = d.temperatureBias
    Caption(
        if (bias == null) {
            stringResource(R.string.derived_bias_none)
        } else {
            stringResource(R.string.derived_bias, signed(-bias.biasC), bias.samples, signed(bias.altitudeAdjustmentC))
        },
    )
    Caption(stringResource(R.string.derived_streaks, d.streaks.hotDays, d.streaks.coldDays))
}

@Composable
private fun WindLine(label: String, w: WindSummary) {
    Text(
        stringResource(
            R.string.derived_wind,
            label,
            Formatting.compass(w.dominantDirectionDeg),
            number(w.meanSpeedKmh),
            number(w.persistence * 100, 0),
            number(w.maxGustKmh),
        ),
    )
}

@Composable
private fun RainLines(d: DerivedConditions) {
    val r = d.rain
    val since = r.hoursSinceRain?.let { ageText(Duration.ofHours(it)) } ?: stringResource(R.string.derived_rain_never)
    Text(stringResource(R.string.derived_rain, number(r.last24hMm), number(r.last72hMm), number(r.antecedentIndexMm), since))
    Caption(stringResource(if (r.runoffLikely) R.string.derived_runoff_yes else R.string.derived_runoff_no))
}

@Composable
private fun ReservoirLine(d: DerivedConditions) {
    val level = d.reservoir
    if (level == null) {
        Caption(stringResource(R.string.derived_reservoir_missing))
        return
    }
    val delta = level.delta7dHm3?.let { stringResource(R.string.derived_reservoir_delta, signed(it)) }
        ?: stringResource(R.string.derived_reservoir_no_delta)
    Text(stringResource(R.string.derived_reservoir, number(level.volumeHm3), number(level.percent, 0), delta))
}

@Composable
private fun WeightZeroLines(d: DerivedConditions) {
    Caption(stringResource(R.string.derived_weight_zero_title))
    val p = d.pressure
    Caption(stringResource(R.string.derived_pressure, signed(p.delta3hHpa), signed(p.delta24hHpa), signed(p.delta72hHpa), number(p.stdDev48hHpa)))
    d.cloudCover24hPct?.let { Caption(stringResource(R.string.derived_clouds, number(it, 0))) }
    val phase = stringResource(
        when (d.moon.phase) {
            MoonPhaseName.NEW -> R.string.moon_new
            MoonPhaseName.WAXING_CRESCENT -> R.string.moon_waxing_crescent
            MoonPhaseName.FIRST_QUARTER -> R.string.moon_first_quarter
            MoonPhaseName.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
            MoonPhaseName.FULL -> R.string.moon_full
            MoonPhaseName.WANING_GIBBOUS -> R.string.moon_waning_gibbous
            MoonPhaseName.LAST_QUARTER -> R.string.moon_last_quarter
            MoonPhaseName.WANING_CRESCENT -> R.string.moon_waning_crescent
        },
    )
    Caption(stringResource(R.string.derived_moon, phase, number(d.moon.illumination * 100, 0)))
}

@Composable
private fun ageText(age: Duration): String {
    val (unit, value) = Formatting.ageParts(age)
    return when (unit) {
        Formatting.AgeUnit.NOW -> stringResource(R.string.age_now)
        Formatting.AgeUnit.MINUTES -> stringResource(R.string.age_minutes, value.toInt())
        Formatting.AgeUnit.HOURS -> stringResource(R.string.age_hours, value.toInt())
        Formatting.AgeUnit.DAYS -> stringResource(R.string.age_days, value.toInt())
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
