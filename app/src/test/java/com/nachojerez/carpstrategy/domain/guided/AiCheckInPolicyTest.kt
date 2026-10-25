package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiCheckInPolicyTest {
    private val t0 = Instant.parse("2026-10-25T08:00:00Z")
    private fun at(min: Long) = t0.plus(Duration.ofMinutes(min))
    private fun consulted(min: Long) = AiExchange(at(min), AiKind.CHECK_IN, "GEMINI", "m", valid = true)
    private fun record(checkIns: List<CheckIn> = emptyList(), ai: List<AiExchange> = emptyList(), weather: List<WeatherSnapshot> = emptyList(), conditions: List<ConditionChange> = emptyList()) =
        GuidedRecord(
            rods = listOf(RodTrack(1, "Caña 1", GuidedLog(listOf(Segment(start = t0, kind = StepKind.INITIAL, checkIns = checkIns))))),
            ai = ai,
            weather = weather,
            conditions = conditions,
        )

    @Test
    fun `la primera vez se pregunta y despues solo si cambia algo`() {
        assertEquals(setOf(AiTrigger.FIRST), AiCheckInPolicy.triggers(record(), at(20)))
        val quiet = record(checkIns = listOf(CheckIn(at(40))), ai = listOf(consulted(20)))
        assertTrue(AiCheckInPolicy.triggers(quiet, at(60)).isEmpty())
        assertFalse(AiCheckInPolicy.shouldConsult(quiet, AiCheckInMode.ON_CHANGE, at(60)))
        assertTrue(AiCheckInPolicy.shouldConsult(quiet, AiCheckInMode.ALWAYS, at(60)))
        assertFalse(AiCheckInPolicy.shouldConsult(record(), AiCheckInMode.NEVER, at(20)))
        // Sin cambios, como mucho cada 2 horas.
        assertEquals(setOf(AiTrigger.LONG_QUIET), AiCheckInPolicy.triggers(quiet, at(20 + 120)))
    }

    @Test
    fun `cambios que justifican preguntar`() {
        val ai = listOf(consulted(20))
        // Solo cuenta lo que pasa después de la última consulta.
        assertTrue(AiCheckInPolicy.triggers(record(checkIns = listOf(CheckIn(at(10), notWorking = true)), ai = ai), at(40)).isEmpty())
        assertEquals(setOf(AiTrigger.NOT_WORKING), AiCheckInPolicy.triggers(record(checkIns = listOf(CheckIn(at(30), notWorking = true)), ai = ai), at(40)))
        assertEquals(
            setOf(AiTrigger.OTHER_FISH),
            AiCheckInPolicy.triggers(record(checkIns = listOf(CheckIn(at(30), activity = HookActivity.CATCH, species = Species.BARBEL)), ai = ai), at(40)),
        )
        assertEquals(setOf(AiTrigger.TOUCHES), AiCheckInPolicy.triggers(record(checkIns = listOf(CheckIn(at(30), activity = HookActivity.MISSED)), ai = ai), at(40)))
        assertEquals(
            setOf(AiTrigger.CONDITIONS),
            AiCheckInPolicy.triggers(record(conditions = listOf(ConditionChange(at(30), FieldCondition.LIGHT_RAIN, true)), ai = ai), at(40)),
        )
        val windUp = listOf(WeatherSnapshot(at(15), windKmh = 5.0), WeatherSnapshot(at(35), windKmh = 20.0))
        assertEquals(setOf(AiTrigger.WEATHER), AiCheckInPolicy.triggers(record(weather = windUp, ai = ai), at(40)))
        val same = listOf(WeatherSnapshot(at(15), windKmh = 5.0), WeatherSnapshot(at(35), windKmh = 6.0))
        assertTrue(AiCheckInPolicy.triggers(record(weather = same, ai = ai), at(40)).isEmpty())
    }
}
