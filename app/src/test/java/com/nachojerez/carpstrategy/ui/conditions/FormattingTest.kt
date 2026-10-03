package com.nachojerez.carpstrategy.ui.conditions

import com.nachojerez.carpstrategy.domain.derived.VariableStats
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FormattingTest {
    @Test
    fun `numeros con coma decimal y guion si faltan`() {
        assertEquals("24,4", Formatting.number(24.4))
        assertEquals("1019,2", Formatting.number(1019.2))
        assertEquals("305", Formatting.number(305.0, 0))
        assertEquals("—", Formatting.number(null))
    }

    @Test
    fun `media con dispersion solo si hay varios modelos`() {
        assertEquals("25,1 ±1,9", Formatting.withSpread(VariableStats(25.133, 1.9, 3)))
        assertEquals("20,0", Formatting.withSpread(VariableStats(20.0, 0.0, 1)))
        assertEquals("—", Formatting.withSpread(null))
    }

    @ParameterizedTest
    @CsvSource("0, N", "22, N", "23, NE", "90, E", "180, S", "225, SO", "270, O", "315, NO", "359, N", "-90, O", "720, N")
    fun `rumbo en ocho puntos`(degrees: Double, expected: String) {
        assertEquals(expected, Formatting.compass(degrees))
    }

    @Test
    fun `hora local de Madrid`() {
        // 25/09/2026 22:00 UTC = sábado 26/09 00:00 en Madrid (CEST).
        assertEquals("sáb 26 00:00", Formatting.hour(Instant.parse("2026-09-25T22:00:00Z")))
    }

    @Test
    fun `antiguedad en la unidad mas util`() {
        assertEquals(Formatting.AgeUnit.NOW to 0L, Formatting.ageParts(Duration.ofSeconds(30)))
        assertEquals(Formatting.AgeUnit.MINUTES to 59L, Formatting.ageParts(Duration.ofMinutes(59)))
        assertEquals(Formatting.AgeUnit.HOURS to 30L, Formatting.ageParts(Duration.ofHours(30)))
        assertEquals(Formatting.AgeUnit.DAYS to 3L, Formatting.ageParts(Duration.ofDays(3)))
    }
}
