package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/*
 * Preparar la salida (fase 7.15): con cobertura (la noche antes) se descarga la previsión, se pide el
 * plan de la IA para la mañana siguiente y se completan las fichas de cebos pendientes. En el
 * embalse, sin red, el plan preparado sigue valiendo toda la jornada.
 */

/** A esa hora ya estás pescando y las reglas valoran el amanecer, no la noche. 🟣 */
val PREP_AFTER_LEGAL_START: Duration = Duration.ofMinutes(30)

/** La próxima salida: hoy si aún no ha empezado el horario legal; si no, mañana. Null sin orto (no pasa en España). */
fun nextOuting(now: Instant, point: GeoPoint, zone: ZoneId): Instant? {
    val today = now.atZone(zone).toLocalDate()
    val todayStart = LegalWindow.of(SolarCalculator.sunTimes(today, point))?.start
    val day = if (todayStart != null && now.isBefore(todayStart)) today else today.plusDays(1)
    return LegalWindow.of(SolarCalculator.sunTimes(day, point))?.start?.plus(PREP_AFTER_LEGAL_START)
}

/** El [planKey] sin la franja del día: un plan preparado vale para toda la jornada. */
fun outingKey(planKey: String?): String? = planKey?.split("|")?.filterIndexed { i, _ -> i != DAY_PART_INDEX }?.joinToString("|")

/** Posición de la franja del día en [planKey]. */
private const val DAY_PART_INDEX = 4

/**
 * El plan de la IA que vale para lo que tienes elegido ahora: el pedido para esto mismo o, si no,
 * el preparado para esta salida (mismo día, embalse, puesto, cañas y cebos).
 */
fun aiPlanFor(state: StrategyUiState, plan: PlanUiState): AiOutcome<AiPlan>? {
    val key = planKey(state) ?: return null
    plan.outcome?.takeIf { plan.key == key }?.let { return it }
    return plan.prepared?.takeIf { plan.preparedKey != null && outingKey(plan.preparedKey) == outingKey(key) }
}

/** Pasos de «Preparar mañana». */
enum class PrepStep { FORECAST, PLAN, SHEETS }

/** Cómo quedó el plan preparado. */
enum class PrepPlan { READY, FAILED, NO_KEY, NOTHING }

data class PrepResult(
    /** Hora de la salida para la que se preparó. */
    val target: Instant,
    val forecastOk: Boolean,
    val plan: PrepPlan,
    /** Qué dijo el proveedor si el plan falló (código y mensaje). */
    val planDetail: String? = null,
    val sheetsImproved: Int = 0,
    val sheetsPending: Int = 0,
)

data class PrepUiState(val step: PrepStep? = null, val result: PrepResult? = null) {
    val running: Boolean get() = step != null
}
