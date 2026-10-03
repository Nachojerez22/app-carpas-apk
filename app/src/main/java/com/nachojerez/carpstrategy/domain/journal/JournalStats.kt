package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.StrategyResult
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Resumen de un conjunto de sesiones terminadas. La métrica es capturas por hora-caña (§10). */
data class JournalSummary(
    val sessions: Int,
    val rodHours: Double,
    val catches: Int,
    val blanks: Int,
    val bites: Int,
) {
    /** Null si no hay esfuerzo registrado. */
    val catchesPerRodHour: Double? get() = if (rodHours > 0.0) catches / rodHours else null

    companion object {
        val EMPTY = JournalSummary(0, 0.0, 0, 0, 0)
    }
}

/** Funciones puras sobre el diario. */
object JournalStats {
    /** Capturas en un puesto durante estos días bajan su capturabilidad (§5.8, 🟢): sugerir rotar. */
    val RECENT_CATCH_WINDOW: Duration = Duration.ofDays(14)

    /** Una valoración solo cuenta como "previa" si se calculó hasta 24 h antes del inicio. */
    val PREDICTION_MAX_AGE: Duration = Duration.ofHours(24)

    /** Radio para aceptar una valoración calculada en otro punto (mismo embalse). */
    const val PREDICTION_RADIUS_KM = 10.0

    fun summarize(sessions: List<Session>): JournalSummary {
        val finished = sessions.filterNot { it.isOngoing }
        return JournalSummary(
            sessions = finished.size,
            rodHours = finished.sumOf { it.rodHours ?: 0.0 },
            catches = finished.sumOf { it.catches.size },
            blanks = finished.count { it.isBlank },
            bites = finished.sumOf { it.bites },
        )
    }

    fun summarizeMonth(sessions: List<Session>, month: YearMonth, zone: ZoneId): JournalSummary =
        summarize(sessions.filter { YearMonth.from(it.start.atZone(zone)) == month })

    /**
     * Resultado real agrupado por la valoración que dio la app antes de cada sesión. Con pocas
     * sesiones las diferencias no son concluyentes (§10): la UI muestra el número de sesiones.
     */
    fun byPredictedBand(sessions: List<Session>): Map<FavorabilityBand, JournalSummary> =
        sessions.filter { it.prediction?.band != null }
            .groupBy { it.prediction!!.band!! }
            .mapValues { (_, list) -> summarize(list) }
            .filterValues { it.sessions > 0 }

    /** Última captura por zona dentro de [RECENT_CATCH_WINDOW] antes de [now]. */
    fun recentCatchZones(sessions: List<Session>, now: Instant): Map<FishingZone, Instant> {
        val from = now.minus(RECENT_CATCH_WINDOW)
        return sessions
            .filter { it.zone != null && it.catches.isNotEmpty() }
            .mapNotNull { s ->
                val last = s.catches.mapNotNull { it.time }.maxOrNull() ?: s.end ?: s.start
                if (last.isBefore(from) || last.isAfter(now)) null else s.zone!! to last
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }
    }

    /**
     * Valoración previa para una sesión que empieza en [start]: la más reciente calculada antes
     * del inicio (como mucho [PREDICTION_MAX_AGE] antes) y cerca del lugar. Null si no hay.
     */
    fun predictionBefore(snapshots: List<PredictionSnapshot>, start: Instant, location: GeoPoint): PredictionSnapshot? =
        snapshots
            .filter { !it.computedAt.isAfter(start) && !it.computedAt.isBefore(start.minus(PREDICTION_MAX_AGE)) }
            .filter { Geo.haversineKm(it.location, location) <= PREDICTION_RADIUS_KM }
            .maxByOrNull { it.computedAt }

    fun snapshotOf(result: StrategyResult, location: GeoPoint): PredictionSnapshot = PredictionSnapshot(
        computedAt = result.evaluatedAt,
        location = location,
        blocked = result.blocked,
        favorability = result.favorability,
        band = result.band,
        limitingLevel = result.limitingLevel,
        demand = result.demand,
    )

    /** Contexto automático de la sesión a partir de los derivados calculados para su inicio. */
    fun contextOf(derived: DerivedConditions, pressureHpa: Double?): SessionContext = SessionContext(
        legalStart = derived.legalToday?.start,
        legalEnd = derived.legalToday?.end,
        waterTempC = derived.water?.valueC,
        waterMeasured = derived.water?.source == WaterTempSource.MEASURED,
        airTemp24hC = derived.airTrend?.last24hMeanC,
        windKmh = derived.wind24h?.meanSpeedKmh,
        windDirectionDeg = derived.wind24h?.dominantDirectionDeg,
        rain72hMm = derived.rain.last72hMm,
        reservoirPercent = derived.reservoir?.percent,
        moonIllumination = derived.moon.illumination,
        pressureHpa = pressureHpa,
    )
}
