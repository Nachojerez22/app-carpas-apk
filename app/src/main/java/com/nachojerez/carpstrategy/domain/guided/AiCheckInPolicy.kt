package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.assistant.AiKind
import java.time.Duration
import java.time.Instant

/** Cuándo se consulta a la IA en los avisos de la sesión guiada (fase 7.15). */
enum class AiCheckInMode { NEVER, ON_CHANGE, ALWAYS }

/** Por qué merece la pena preguntar a la IA en este aviso. */
enum class AiTrigger { FIRST, WEATHER, CONDITIONS, NOT_WORKING, OTHER_FISH, TOUCHES, LONG_QUIET }

/**
 * Preguntar a la IA en cada aviso gasta mucha cuota en una jornada larga y casi siempre contesta
 * «mantener». Con [AiCheckInMode.ON_CHANGE] solo se pregunta si algo cambió desde la última
 * consulta; lo demás lo resuelven las reglas de la app, como siempre.
 */
object AiCheckInPolicy {
    /** Sin cambios, una consulta cada tanto para revisar la estrategia. 🟣 */
    val MAX_QUIET: Duration = Duration.ofMinutes(120)

    fun shouldConsult(record: GuidedRecord, mode: AiCheckInMode, now: Instant): Boolean = when (mode) {
        AiCheckInMode.NEVER -> false
        AiCheckInMode.ALWAYS -> true
        AiCheckInMode.ON_CHANGE -> triggers(record, now).isNotEmpty()
    }

    fun triggers(record: GuidedRecord, now: Instant): Set<AiTrigger> {
        val last = record.ai.filter { it.kind == AiKind.CHECK_IN }.maxOfOrNull { it.time } ?: return setOf(AiTrigger.FIRST)
        val since = record.allCheckIns.filter { it.time.isAfter(last) }
        return buildSet {
            val before = record.weather.lastOrNull { !it.time.isAfter(last) }
            val latest = record.weather.lastOrNull()
            val weatherChanged = before != null && latest != null && latest !== before && FieldWeather.changes(before, latest).isNotEmpty()
            if (weatherChanged || record.windChanges.any { it.isAfter(last) }) add(AiTrigger.WEATHER)
            if (record.conditions.any { it.time.isAfter(last) }) add(AiTrigger.CONDITIONS)
            if (since.any { it.notWorking }) add(AiTrigger.NOT_WORKING)
            if (since.any { it.isBycatch }) add(AiTrigger.OTHER_FISH)
            if (since.any { it.activity == HookActivity.TOUCHES || it.activity == HookActivity.MISSED }) add(AiTrigger.TOUCHES)
            if (Duration.between(last, now) >= MAX_QUIET) add(AiTrigger.LONG_QUIET)
        }
    }
}
