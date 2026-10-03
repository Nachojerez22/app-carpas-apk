package com.nachojerez.carpstrategy.domain.rules

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SeasonByWater
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionWindowsAndContextTest {
    private val sun = SolarCalculator.sunTimes(LocalDate.parse("2026-10-03"), GeoPoint(38.35, -6.70))
    private val legal = LegalWindow.of(sun)!!

    private fun kinds(season: SeasonByWater?, trend: Double? = null) =
        SessionWindows.suggest(legal, sun, season, trend).map { it.kind }

    @Test
    fun `ventanas por estacion del agua`() {
        assertEquals(listOf(WindowKind.MIDDAY), kinds(SeasonByWater.WINTER))
        assertEquals(listOf(WindowKind.DAWN, WindowKind.DUSK), kinds(SeasonByWater.SUMMER))
        assertEquals(listOf(WindowKind.MIDDAY, WindowKind.DUSK), kinds(SeasonByWater.AUTUMN))
        assertEquals(listOf(WindowKind.AFTERNOON_DUSK), kinds(SeasonByWater.SPRING, trend = 0.0))
        assertEquals(listOf(WindowKind.DAWN, WindowKind.AFTERNOON_DUSK), kinds(SeasonByWater.SPRING, trend = 1.0))
        assertEquals(listOf(WindowKind.DAWN, WindowKind.DUSK), kinds(null))
    }

    @Test
    fun `todas las ventanas caen dentro del horario legal`() {
        SeasonByWater.entries.plus(null).forEach { season ->
            SessionWindows.suggest(legal, sun, season, 1.0).forEach { w ->
                assertTrue(w.start in legal && w.end in legal, "$season $w fuera de $legal")
                assertTrue(w.start.isBefore(w.end))
            }
        }
        val dawn = SessionWindows.suggest(legal, sun, SeasonByWater.SUMMER, null).first()
        assertEquals(legal.start, dawn.start)
        assertEquals(sun.sunrise!!.plus(Duration.ofHours(2)), dawn.end)
    }

    @Test
    fun `sin horario legal no hay ventanas`() {
        assertTrue(SessionWindows.suggest(null, sun, SeasonByWater.SUMMER, null).isEmpty())
    }

    @Test
    fun `rumbos para los textos de viento`() {
        assertEquals("SO", RuleContextBuilder.compass(225.0))
        assertEquals("NE", RuleContextBuilder.compass(225.0 + 180))
        assertEquals("N", RuleContextBuilder.compass(355.0))
    }
}
