package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.guided.GuidedRecord
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.RodTrack
import com.nachojerez.carpstrategy.domain.guided.Segment
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.FeatureSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionContext
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExperienceTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val point = GeoPoint(38.35, -6.70)

    // 10 de octubre de 2026: amanecer ≈ 08:20 local, ocaso ≈ 19:50 local.
    private fun t(day: Int, hhmm: String): Instant = Instant.parse("2026-10-%02dT%s:00Z".format(day, hhmm))

    private fun autumn(water: Double) = SessionContext(waterTempC = water, features = FeatureSnapshot(numbers = mapOf("temp_agua_c" to water), texts = mapOf("estacion" to "otono")))

    /** Guiada: caña 1 con chufa a fondo (2 carpas), caña 2 con maíz (nada). 4 h cada una. */
    private fun guided(id: Long, day: Int): Session {
        val start = t(day, "06:30")
        val end = start.plus(Duration.ofHours(4))
        fun seg(bait: BaitType, name: String, catches: Int) = Segment(
            start = start, end = end, kind = StepKind.INITIAL, bait = bait, baitName = name, column = Column.BOTTOM,
            checkIns = (1..catches).map { CheckIn(start.plus(Duration.ofMinutes(30L * it)), activity = HookActivity.CATCH) },
        )
        return Session(
            id = id, start = start, end = end, location = point, rods = 2, createdAt = start, context = autumn(17.0),
            guided = GuidedRecord(
                rods = listOf(RodTrack(1, "fija", GuidedLog(listOf(seg(BaitType.TIGERNUT, "Chufa", 2)))), RodTrack(2, "", GuidedLog(listOf(seg(BaitType.MAIZE, "Maíz", 0))))),
                spot = Spot(1, "Punta"),
            ),
        )
    }

    @Test
    fun `unidades por tramo en las guiadas y por sesion en las demas`() {
        val manual = Session(
            id = 9, start = t(5, "08:00"), end = t(5, "12:00"), location = point, rods = 3, bait = "Boilie fresa",
            catches = listOf(Catch(time = t(5, "09:00"))), createdAt = t(5, "08:00"), context = autumn(18.0),
        )
        val ongoing = Session(id = 10, start = t(6, "08:00"), location = point, createdAt = t(6, "08:00"))
        val units = Experience.units(listOf(guided(1, 10), manual, ongoing))
        assertEquals(3, units.size)
        assertEquals(4.0, units.first().rodHours, 1e-9)
        assertEquals(12.0, units.last().rodHours, 1e-9)
        assertEquals("Punta", units.first().spotName)
        assertEquals(FishingPhase.AUTUMN, units.first().phase)
    }

    @Test
    fun `lo que te ha funcionado en condiciones parecidas`() {
        val sessions = listOf(guided(1, 10), guided(2, 11))
        val summary = Experience.forConditions(sessions, FishingPhase.AUTUMN, 17.5, madrid)
        assertEquals(2, summary.sessions)
        assertEquals(16.0, summary.rodHours, 1e-9)
        assertEquals(4, summary.catches)
        val chufa = summary.baits.first()
        assertEquals("Chufa", chufa.value)
        assertEquals(0.5, chufa.perRodHour, 1e-9)
        assertTrue(summary.proven(chufa))
        assertFalse(summary.proven(summary.baits.last()))
        assertEquals(BaitType.TIGERNUT, summary.baitTypes.first().value)
        // 06:30–10:30 UTC = 08:30–12:30 local: las capturas (07:00–08:00 UTC) caen al amanecer.
        val best = summary.dayParts.first()
        assertEquals(DayPart.DAWN, best.value)
        assertEquals(4, best.catches)
    }

    @Test
    fun `condiciones distintas no cuentan`() {
        val summary = Experience.forConditions(listOf(guided(1, 10)), FishingPhase.WINTER, 8.0, madrid)
        assertEquals(ExperienceSummary.EMPTY, summary)
        // Mismo rango de agua aunque otra fase: sí cuenta.
        assertEquals(1, Experience.forConditions(listOf(guided(1, 10)), FishingPhase.SPRING, 15.0, madrid).sessions)
    }
}
