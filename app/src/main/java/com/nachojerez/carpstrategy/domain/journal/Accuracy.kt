package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Tasa por hora-caña con su intervalo (§10.3, §10.9 y §10.10): Poisson-gamma contraída hacia tu
 * media de todas las sesiones, así un grupo con pocas horas no parece mejor o peor de lo que es.
 */
data class RateEstimate(val events: Int, val rodHours: Double, val mean: Double, val low: Double, val high: Double)

data class BandAccuracy(val sessions: Int, val catches: RateEstimate, val bites: RateEstimate)

/** Qué dicen tus datos de la valoración previa de la app. */
enum class AccuracyVerdict {
    /** Menos de [Accuracy.MIN_SESSIONS_PER_SIDE] sesiones con valoración favorable o desfavorable. */
    TOO_FEW,

    /** Con valoración favorable pescas claramente más (los intervalos no se tocan). */
    AGREES,

    /** Con valoración favorable pescas claramente menos. */
    DISAGREES,

    /** Hay datos, pero los intervalos se solapan: aún no se distingue. */
    UNCLEAR,
}

data class AccuracyReport(
    val byBand: Map<FavorabilityBand, BandAccuracy>,
    /** Favorable y muy favorable juntas. */
    val favorable: BandAccuracy?,
    /** Desfavorable y muy desfavorable juntas. */
    val unfavorable: BandAccuracy?,
    val verdict: AccuracyVerdict,
)

/** ¿Acierta la app con tus datos? (fase 7.16). Función pura. */
object Accuracy {
    /** Peso de tu media, en horas-caña: con menos esfuerzo, el grupo se parece a tu media (contracción). 🟣 */
    const val PRIOR_ROD_HOURS = 10.0

    /** §10.9: no concluir con menos de ~10 sesiones en cada lado. */
    const val MIN_SESSIONS_PER_SIDE = 10

    /** Intervalo del 80 % (z = 1,28): con pocos datos, uno del 95 % no distingue nada. */
    const val Z = 1.2816

    private val FAVORABLE = setOf(FavorabilityBand.FAVORABLE, FavorabilityBand.VERY_FAVORABLE)
    private val UNFAVORABLE = setOf(FavorabilityBand.UNFAVORABLE, FavorabilityBand.VERY_UNFAVORABLE)

    fun report(sessions: List<Session>): AccuracyReport {
        val rated = sessions.filter { !it.isOngoing && it.prediction?.band != null && (it.rodHours ?: 0.0) > 0.0 }
        val hours = rated.sumOf { it.rodHours ?: 0.0 }
        val catchRate = if (hours > 0) rated.sumOf { it.catches.size } / hours else 0.0
        val biteRate = if (hours > 0) rated.sumOf { it.bites } / hours else 0.0
        fun group(list: List<Session>): BandAccuracy? {
            if (list.isEmpty()) return null
            val h = list.sumOf { it.rodHours ?: 0.0 }
            return BandAccuracy(list.size, estimate(list.sumOf { it.catches.size }, h, catchRate), estimate(list.sumOf { it.bites }, h, biteRate))
        }
        val byBand = rated.groupBy { it.prediction!!.band!! }.mapNotNull { (band, list) -> group(list)?.let { band to it } }.toMap()
        val favList = rated.filter { it.prediction!!.band in FAVORABLE }
        val unfList = rated.filter { it.prediction!!.band in UNFAVORABLE }
        val fav = group(favList)
        val unf = group(unfList)
        val verdict = when {
            fav == null || unf == null || favList.size < MIN_SESSIONS_PER_SIDE || unfList.size < MIN_SESSIONS_PER_SIDE -> AccuracyVerdict.TOO_FEW
            // Primero las capturas; si son tan pocas que no distinguen, las picadas (§10.8).
            separated(fav.catches, unf.catches) -> AccuracyVerdict.AGREES
            separated(unf.catches, fav.catches) -> AccuracyVerdict.DISAGREES
            separated(fav.bites, unf.bites) -> AccuracyVerdict.AGREES
            separated(unf.bites, fav.bites) -> AccuracyVerdict.DISAGREES
            else -> AccuracyVerdict.UNCLEAR
        }
        return AccuracyReport(byBand, fav, unf, verdict)
    }

    /** [a] claramente por encima de [b]: su intervalo empieza donde acaba el otro. */
    private fun separated(a: RateEstimate, b: RateEstimate): Boolean = a.low > b.high

    /**
     * Posterior Gamma(α₀ + eventos, β₀ + horas) con α₀ = tu tasa × [PRIOR_ROD_HOURS] + ½ y
     * β₀ = [PRIOR_ROD_HOURS]. El intervalo usa la aproximación de Wilson-Hilferty.
     */
    fun estimate(events: Int, rodHours: Double, priorRate: Double): RateEstimate {
        val shape = priorRate * PRIOR_ROD_HOURS + 0.5 + events
        val rate = PRIOR_ROD_HOURS + rodHours
        return RateEstimate(events, rodHours, shape / rate, quantile(shape, rate, -Z), quantile(shape, rate, Z))
    }

    private fun quantile(shape: Double, rate: Double, z: Double): Double {
        val c = 1.0 / (9.0 * shape)
        return max(0.0, shape * (1.0 - c + z * sqrt(c)).pow(3) / rate)
    }
}
