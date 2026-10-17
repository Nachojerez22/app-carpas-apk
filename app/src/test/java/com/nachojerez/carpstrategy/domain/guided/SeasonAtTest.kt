package com.nachojerez.carpstrategy.domain.guided

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeasonAtTest {
    @Test
    fun `sesiones guardadas con primavera en otoño se corrigen por la fecha`() {
        val october = LocalDate.parse("2026-10-17")
        assertEquals("otono", GuidedSessions.seasonAt("primavera", october))
        assertEquals("primavera", GuidedSessions.seasonAt("otono", LocalDate.parse("2026-04-10")))
        assertEquals("verano", GuidedSessions.seasonAt("verano", october))
        assertEquals("invierno", GuidedSessions.seasonAt("invierno", october))
        assertEquals(null, GuidedSessions.seasonAt(null, october))
        assertEquals(FishingPhase.AUTUMN, GuidedEngine.phase(19.2, GuidedSessions.seasonAt("primavera", october)))
    }
}
