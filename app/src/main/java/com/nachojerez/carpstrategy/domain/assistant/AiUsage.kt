package com.nachojerez.carpstrategy.domain.assistant

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Una consulta a la IA tal como la cuenta la app (fase 7.14). [kind] null = prueba de conexión. */
data class AiCall(
    val time: Instant,
    val kind: AiKind?,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val thinkingTokens: Long = 0,
    /** Búsquedas en Google que hizo el modelo en esta consulta. */
    val searches: Int = 0,
    /** Google respondió «cuota agotada» (429): no cuenta como consulta, sí como error. */
    val quota: Boolean = false,
    val quotaDetail: String? = null,
)

/** Lo gastado en un día de cuota de Google. */
data class UsageDay(
    val day: LocalDate,
    val requests: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val thinkingTokens: Long = 0,
    val searches: Int = 0,
    val quotaErrors: Int = 0,
    val byKind: Map<AiKind, Int> = emptyMap(),
    val lastQuotaAt: Instant? = null,
    val lastQuotaDetail: String? = null,
) {
    val tokens: Long get() = inputTokens + outputTokens + thinkingTokens
}

/** Tus límites diarios (de Google AI Studio → Rate limits); null = sin límite puesto. */
data class AiLimits(val requestsPerDay: Int? = null, val searchesPerDay: Int? = null, val tokensPerDay: Long? = null)

/** Cómo vas respecto a un límite. */
enum class LimitLevel { OK, NEAR, OVER }

/**
 * Consumo de la IA (fase 7.14). Gemini no dice cuánta cuota gratuita te queda: la app cuenta lo
 * que gasta (consultas, tokens, búsquedas y errores de cuota) por día de cuota de Google, que se
 * renueva a medianoche del Pacífico. Cada móvil guarda sus días y la pantalla suma los de todos
 * (la cuota es de la clave, compartida). Funciones puras.
 */
object AiUsage {
    /** Zona en la que Google renueva las cuotas diarias. */
    val QUOTA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

    /** Días que se guardan. */
    const val KEEP_DAYS = 7

    /** Aviso a partir de esta fracción del límite. */
    const val NEAR_FRACTION = 0.8

    fun dayOf(time: Instant): LocalDate = time.atZone(QUOTA_ZONE).toLocalDate()

    /** Cuándo se renueva la cuota del día de [now]. */
    fun nextReset(now: Instant): Instant = dayOf(now).plusDays(1).atStartOfDay(QUOTA_ZONE).toInstant()

    /** Añade una consulta y deja solo los últimos [KEEP_DAYS] días (del más reciente al más antiguo). */
    fun record(days: List<UsageDay>, call: AiCall): List<UsageDay> {
        val day = dayOf(call.time)
        val current = days.firstOrNull { it.day == day } ?: UsageDay(day)
        val updated = if (call.quota) {
            current.copy(quotaErrors = current.quotaErrors + 1, lastQuotaAt = call.time, lastQuotaDetail = call.quotaDetail ?: current.lastQuotaDetail)
        } else {
            current.copy(
                requests = current.requests + 1,
                inputTokens = current.inputTokens + call.inputTokens,
                outputTokens = current.outputTokens + call.outputTokens,
                thinkingTokens = current.thinkingTokens + call.thinkingTokens,
                searches = current.searches + call.searches,
                byKind = call.kind?.let { k -> current.byKind + (k to (current.byKind[k] ?: 0) + 1) } ?: current.byKind,
            )
        }
        val oldest = day.minusDays((KEEP_DAYS - 1).toLong())
        return (days.filterNot { it.day == day } + updated).filter { !it.day.isBefore(oldest) }.sortedByDescending { it.day }
    }

    /** Suma los días de todos los móviles. */
    fun merge(devices: List<List<UsageDay>>): List<UsageDay> = devices.flatten().groupBy { it.day }.map { (day, list) ->
        val lastQuota = list.filter { it.lastQuotaAt != null }.maxByOrNull { it.lastQuotaAt!! }
        UsageDay(
            day = day,
            requests = list.sumOf { it.requests },
            inputTokens = list.sumOf { it.inputTokens },
            outputTokens = list.sumOf { it.outputTokens },
            thinkingTokens = list.sumOf { it.thinkingTokens },
            searches = list.sumOf { it.searches },
            quotaErrors = list.sumOf { it.quotaErrors },
            byKind = list.flatMap { it.byKind.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() },
            lastQuotaAt = lastQuota?.lastQuotaAt,
            lastQuotaDetail = lastQuota?.lastQuotaDetail,
        )
    }.sortedByDescending { it.day }

    fun today(days: List<UsageDay>, now: Instant): UsageDay = dayOf(now).let { d -> days.firstOrNull { it.day == d } ?: UsageDay(d) }

    /** Los últimos [KEEP_DAYS] días hasta hoy, con los días sin uso a cero. */
    fun lastDays(days: List<UsageDay>, now: Instant): List<UsageDay> {
        val today = dayOf(now)
        return (0 until KEEP_DAYS).map { i -> today.minusDays(i.toLong()).let { d -> days.firstOrNull { it.day == d } ?: UsageDay(d) } }
    }

    fun level(used: Long, limit: Long?): LimitLevel = when {
        limit == null || limit <= 0 -> LimitLevel.OK
        used >= limit -> LimitLevel.OVER
        used >= limit * NEAR_FRACTION -> LimitLevel.NEAR
        else -> LimitLevel.OK
    }
}
