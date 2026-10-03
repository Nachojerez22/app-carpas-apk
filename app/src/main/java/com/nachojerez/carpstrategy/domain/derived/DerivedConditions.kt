package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.StationObservation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Todos los parámetros derivados en un instante. Cada campo es null si faltan datos. */
data class DerivedConditions(
    val now: Instant,
    val temperatureBias: TemperatureBias?,
    val airTrend: AirTemperatureTrend?,
    val water: WaterTemperature?,
    val season: SeasonClassification?,
    /** 🔴 peso 0: solo se registra. */
    val pressure: PressureTrend,
    val wind24h: WindSummary?,
    val wind48h: WindSummary?,
    val rain: Rainfall,
    val streaks: TemperatureStreaks,
    val reservoir: ReservoirLevel?,
    val sunToday: SunTimes,
    val legalToday: LegalWindow?,
    /** 🔴 peso 0: solo se registra. */
    val moon: MoonInfo,
    /** 🔴 peso 0: nubosidad media de las últimas 24 h (%). */
    val cloudCover24hPct: Double?,
)

/**
 * Calcula los parámetros derivados a partir de la serie combinada (según la prioridad de
 * fuentes del usuario), los modelos, AEMET y los registros manuales. Función pura.
 */
object DerivedCalculator {

    fun compute(
        merged: List<MergedHour>,
        ensemble: List<EnsembleHour>,
        observations: List<StationObservation>,
        stationAltitudeM: Double?,
        gridElevationM: Double?,
        manual: List<ManualRecord>,
        location: GeoPoint,
        now: Instant,
        zone: ZoneId = ZoneId.of("Europe/Madrid"),
        manualEnabled: Boolean = true,
    ): DerivedConditions {
        val bias = TemperatureBiasModel.estimate(ensemble, observations, stationAltitudeM, gridElevationM, now)

        fun series(variable: WeatherVariable, transform: (SourcedValue) -> Double = { it.value }) =
            HourlySeries(merged.mapNotNull { h -> h[variable]?.let { Point(h.time, transform(it)) } })

        // Las temperaturas que vienen de los modelos se corrigen con el sesgo frente a AEMET.
        val air = series(WeatherVariable.AIR_TEMPERATURE) { v ->
            if (v.source == DataSource.MODELS && bias != null) v.value - bias.biasC else v.value
        }
        val pressure = series(WeatherVariable.PRESSURE_MSL)
        val windSpeed = series(WeatherVariable.WIND_SPEED)
        val windDirection = series(WeatherVariable.WIND_DIRECTION)
        val gusts = series(WeatherVariable.WIND_GUSTS)
        val rain = series(WeatherVariable.PRECIPITATION)
        val clouds = series(WeatherVariable.CLOUD_COVER)

        val usableManual = if (manualEnabled) manual else emptyList()
        val waterMeasurements = usableManual.mapNotNull { r ->
            val at = (r.period as? RecordPeriod.At)?.time ?: return@mapNotNull null
            r.values[ManualField.WATER_TEMP_SURFACE]?.let { Point(at, it) }
        }
        val water = WaterTemperatureModel.evaluate(air, waterMeasurements, now)

        val dailyRain = usableManual.mapNotNull { r ->
            val day = (r.period as? RecordPeriod.Day)?.date ?: return@mapNotNull null
            r.values[ManualField.DAILY_PRECIPITATION]?.let { day to it }
        }.toMap()

        val levels = usableManual.mapNotNull { r ->
            val volume = r.values[ManualField.RESERVOIR_VOLUME]
            val percent = r.values[ManualField.RESERVOIR_PERCENT]
            if (volume == null && percent == null) return@mapNotNull null
            LevelReading(r.period.instant(zone), volume, percent, r.source)
        }

        val today: LocalDate = now.atZone(zone).toLocalDate()
        val sun = SolarCalculator.sunTimes(today, location)

        return DerivedConditions(
            now = now,
            temperatureBias = bias,
            airTrend = Trends.airTemperature(air, now),
            water = water,
            season = water?.let { SeasonModel.classify(it.valueC, it.trend3dC, today) },
            pressure = Trends.pressure(pressure, now),
            wind24h = Trends.wind(windSpeed, windDirection, gusts, now, Duration.ofHours(24)),
            wind48h = Trends.wind(windSpeed, windDirection, gusts, now, Duration.ofHours(48)),
            rain = Trends.rainfall(rain, now, zone, dailyRain),
            streaks = Trends.streaks(air, now, zone),
            reservoir = ReservoirModel.evaluate(levels, now),
            sunToday = sun,
            legalToday = LegalWindow.of(sun),
            moon = MoonCalculator.moon(now),
            cloudCover24hPct = clouds.window(now.minus(Duration.ofHours(24)), now).map { it.value }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** Los registros por día cuentan a mediodía local para ordenarlos y compararlos. */
    private fun RecordPeriod.instant(zone: ZoneId): Instant = when (this) {
        is RecordPeriod.At -> time
        is RecordPeriod.Day -> date.atTime(12, 0).atZone(zone).toInstant()
    }
}
