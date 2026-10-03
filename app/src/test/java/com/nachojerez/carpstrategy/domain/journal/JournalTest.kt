package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JournalTest {
    private val brovales = GeoPoint(38.35, -6.70)
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = Instant.parse("2026-09-27T05:20:00Z") // 07:20 en Madrid
    private val end = Instant.parse("2026-09-27T10:40:00Z") // 12:40
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val legal = LegalWindow(Instant.parse("2026-09-27T05:00:00Z"), Instant.parse("2026-09-27T19:30:00Z"))

    private fun session(
        start: Instant = this.start,
        end: Instant? = this.end,
        rods: Int = 3,
        catches: List<Catch> = emptyList(),
        blank: Boolean? = if (catches.isEmpty()) true else false,
        zone: FishingZone? = FishingZone.NORTH,
        band: FavorabilityBand? = null,
    ) = Session(
        start = start, end = end, location = brovales, zone = zone, rods = rods, catches = catches, blank = blank,
        prediction = band?.let { PredictionSnapshot(start.minusSeconds(3600), brovales, false, 0.7, it, null, null) },
        createdAt = now,
    )

    @Test
    fun `horas-cana son canas por duracion salvo que se tecleen`() {
        // 3 cañas × 5 h 20 min = 16,0 (maqueta de la ficha de sesión).
        assertEquals(16.0, session().rodHours!!, 1e-9)
        assertEquals(10.0, session().copy(rodHoursOverride = 10.0).rodHours!!, 1e-9)
        assertNull(session(end = null).rodHours)
    }

    @Test
    fun `una sesion terminada sin capturas exige contestar si fue bolo`() {
        val issues = SessionValidator.validate(session(blank = null), now, listOf(legal)).map { it.code }
        assertEquals(listOf(SessionIssueCode.BLANK_NOT_ANSWERED), issues)
        // En curso todavía no se pregunta.
        assertTrue(SessionValidator.validate(session(end = null, blank = null), start.plusSeconds(60), listOf(legal)).isEmpty())
    }

    @Test
    fun `errores y avisos de la sesion`() {
        val bad = session(end = start.minusSeconds(60), rods = 0)
        val codes = SessionValidator.validate(bad, now, emptyList()).map { it.code }
        assertTrue(SessionIssueCode.END_BEFORE_START in codes)
        assertTrue(SessionIssueCode.INVALID_RODS in codes)

        val withCatches = session(rods = 4, catches = listOf(Catch(time = start.minusSeconds(60), weightKg = 8.4)), blank = true)
        val more = SessionValidator.validate(withCatches, now, listOf(legal))
        assertEquals(
            setOf(SessionIssueCode.RODS_OVER_LIMIT, SessionIssueCode.BLANK_WITH_CATCHES, SessionIssueCode.CATCH_OUTSIDE_SESSION),
            more.map { it.code }.toSet(),
        )
        assertFalse(SessionIssueCode.RODS_OVER_LIMIT.isError)
    }

    @Test
    fun `avisa si la sesion sale del horario legal`() {
        val night = session(start = Instant.parse("2026-09-27T03:00:00Z"))
        assertEquals(listOf(SessionIssueCode.OUTSIDE_LEGAL_HOURS), SessionValidator.validate(night, now, listOf(legal)).map { it.code })
    }

    @Test
    fun `resumen con capturas por hora-cana y bolos`() {
        val catch = session(catches = listOf(Catch(weightKg = 8.4), Catch(weightKg = 6.1)))
        val blank = session(start = start.minus(Duration.ofDays(3)), end = end.minus(Duration.ofDays(3)))
        val ongoing = session(end = null)
        val summary = JournalStats.summarize(listOf(catch, blank, ongoing))
        assertEquals(2, summary.sessions)
        assertEquals(32.0, summary.rodHours, 1e-9)
        assertEquals(2, summary.catches)
        assertEquals(1, summary.blanks)
        assertEquals(2 / 32.0, summary.catchesPerRodHour!!, 1e-9)
        assertEquals(2, JournalStats.summarizeMonth(listOf(catch, blank), YearMonth.of(2026, 9), madrid).sessions)
        assertEquals(0, JournalStats.summarizeMonth(listOf(catch, blank), YearMonth.of(2026, 10), madrid).sessions)
        assertNull(JournalSummary.EMPTY.catchesPerRodHour)
    }

    @Test
    fun `resultado agrupado por la valoracion previa`() {
        val good = session(catches = listOf(Catch()), band = FavorabilityBand.FAVORABLE)
        val bad = session(band = FavorabilityBand.UNFAVORABLE)
        val none = session()
        val byBand = JournalStats.byPredictedBand(listOf(good, bad, none))
        assertEquals(setOf(FavorabilityBand.FAVORABLE, FavorabilityBand.UNFAVORABLE), byBand.keys)
        assertEquals(1, byBand.getValue(FavorabilityBand.FAVORABLE).catches)
        assertEquals(1, byBand.getValue(FavorabilityBand.UNFAVORABLE).blanks)
    }

    @Test
    fun `zonas con capturas recientes para sugerir rotar`() {
        val t = Instant.parse("2026-10-01T18:00:00Z")
        val recent = session(start = t.minusSeconds(3600), end = t, catches = listOf(Catch(time = t)), zone = FishingZone.NORTH)
        val old = session(catches = listOf(Catch()), zone = FishingZone.SOUTH).copy(
            start = now.minus(Duration.ofDays(20)), end = now.minus(Duration.ofDays(20)),
        )
        val blankEast = session(zone = FishingZone.EAST, start = t.minusSeconds(3600), end = t)
        assertEquals(mapOf(FishingZone.NORTH to t), JournalStats.recentCatchZones(listOf(recent, old, blankEast), now))
    }

    @Test
    fun `valoracion previa - la ultima antes del inicio, de las 24 h previas y cerca`() {
        fun snap(at: String, point: GeoPoint = brovales) = PredictionSnapshot(Instant.parse(at), point, false, 0.5, FavorabilityBand.INTERMEDIATE, null, null)
        val snapshots = listOf(
            snap("2026-09-25T20:00:00Z"), // demasiado antigua
            snap("2026-09-26T19:40:00Z"),
            snap("2026-09-27T05:00:00Z", GeoPoint(39.5, -6.0)), // lejos
            snap("2026-09-27T06:00:00Z"), // después del inicio
        )
        assertEquals(Instant.parse("2026-09-26T19:40:00Z"), JournalStats.predictionBefore(snapshots, start, brovales)?.computedAt)
        assertNull(JournalStats.predictionBefore(snapshots.take(1), start, brovales))
    }
}
