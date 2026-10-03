package com.nachojerez.carpstrategy.domain.derived

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Lectura del nivel del embalse en un momento (de registros manuales o importados). */
data class LevelReading(val time: Instant, val volumeHm3: Double?, val percent: Double?, val source: String)

data class ReservoirLevel(
    val latest: LevelReading,
    /** Volumen (hm³), dado o calculado a partir del % con la capacidad oficial. */
    val volumeHm3: Double?,
    val percent: Double?,
    /** Variación en ~7 días (hm³); negativo = vaciando. Null si no hay lectura comparable. */
    val delta7dHm3: Double?,
)

/**
 * Nivel del embalse a partir de las lecturas del usuario. La capacidad oficial de Brovales es
 * 6,98 hm³ (CONOCIMIENTO.md §6.1, dato verificado) y se usa para pasar de % a hm³ y viceversa.
 * El boletín semanal va redondeado a ±0,5 hm³: la variación es orientativa.
 */
object ReservoirModel {
    const val BROVALES_CAPACITY_HM3 = 6.98
    val COMPARISON_TARGET: Duration = Duration.ofDays(7)
    val COMPARISON_TOLERANCE: Duration = Duration.ofDays(3)

    fun evaluate(readings: List<LevelReading>, now: Instant, capacityHm3: Double = BROVALES_CAPACITY_HM3): ReservoirLevel? {
        fun volume(r: LevelReading) = r.volumeHm3 ?: r.percent?.let { it / 100.0 * capacityHm3 }
        val past = readings.filter { !it.time.isAfter(now) && volume(it) != null }.sortedBy { it.time }
        val latest = past.lastOrNull() ?: return null
        val latestVolume = volume(latest)
        val target = latest.time.minus(COMPARISON_TARGET)
        val reference = past
            .filter { it.time.isBefore(latest.time) }
            .minByOrNull { Duration.between(it.time, target).abs() }
            ?.takeIf { Duration.between(it.time, target).abs() <= COMPARISON_TOLERANCE }
        return ReservoirLevel(
            latest = latest,
            volumeHm3 = latestVolume,
            percent = latest.percent ?: latestVolume?.let { it / capacityHm3 * 100.0 },
            delta7dHm3 = reference?.let { ref -> latestVolume?.minus(volume(ref)!!) },
        )
    }
}

enum class SeasonByWater { WINTER, SPRING, SUMMER, AUTUMN }

data class SeasonClassification(
    val season: SeasonByWater,
    /** True si la tendencia era plana y se desempató por el calendario. */
    val fromCalendar: Boolean,
)

/**
 * Estación según la temperatura del agua y su tendencia, no según el calendario
 * (CONOCIMIENTO.md §5.8): invierno < 10 °C, verano > 22 °C, primavera subiendo, otoño bajando.
 * Con tendencia plana entre 10 y 22 °C se desempata por el mes (ene–jun primavera, jul–dic otoño).
 */
object SeasonModel {
    const val WINTER_BELOW_C = 10.0
    const val SUMMER_ABOVE_C = 22.0
    const val TREND_THRESHOLD_C = 0.5

    fun classify(waterC: Double, trend3dC: Double?, date: LocalDate): SeasonClassification = when {
        waterC < WINTER_BELOW_C -> SeasonClassification(SeasonByWater.WINTER, false)
        waterC > SUMMER_ABOVE_C -> SeasonClassification(SeasonByWater.SUMMER, false)
        trend3dC != null && trend3dC >= TREND_THRESHOLD_C -> SeasonClassification(SeasonByWater.SPRING, false)
        trend3dC != null && trend3dC <= -TREND_THRESHOLD_C -> SeasonClassification(SeasonByWater.AUTUMN, false)
        date.monthValue <= 6 -> SeasonClassification(SeasonByWater.SPRING, true)
        else -> SeasonClassification(SeasonByWater.AUTUMN, true)
    }
}
