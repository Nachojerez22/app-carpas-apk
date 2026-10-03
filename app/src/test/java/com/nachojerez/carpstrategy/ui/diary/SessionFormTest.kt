package com.nachojerez.carpstrategy.ui.diary

import com.nachojerez.carpstrategy.data.rules.RulesJson
import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionFormTest {
    private val brovales = GeoPoint(38.35, -6.70)
    private val now = Instant.parse("2026-09-27T11:00:00Z")

    private val form = SessionForm(
        date = "2026-09-27",
        startTime = "7:20",
        endTime = "12.40",
        zone = FishingZone.NORTH,
        rods = "3",
        depth = "2,5",
        catches = listOf(CatchInput("08:05", "8,4", "2", "Carpa común")),
        bites = 4,
    )

    @Test
    fun `convierte el formulario en sesion con hora de Madrid`() {
        val result = form.toSession(null, brovales, now)
        val s = result.session!!
        assertEquals(Instant.parse("2026-09-27T05:20:00Z"), s.start)
        assertEquals(Instant.parse("2026-09-27T10:40:00Z"), s.end)
        assertEquals(2.5, s.depthM)
        assertEquals(listOf(Catch(Instant.parse("2026-09-27T06:05:00Z"), 8.4, 2, "Carpa común")), s.catches)
        assertEquals(false, s.blank)
        assertEquals(16.0, s.rodHours!!, 1e-9)
        assertEquals(now, s.createdAt)
    }

    @Test
    fun `marca los campos que no se entienden`() {
        val bad = form.copy(date = "27/09", startTime = "25:00", rods = "", catches = listOf(CatchInput(weight = "mucho")))
        val result = bad.toSession(null, brovales, now)
        assertNull(result.session)
        assertEquals(setOf(FormField.DATE, FormField.START, FormField.RODS, FormField.CATCH_WEIGHT), result.fieldErrors)
    }

    @Test
    fun `ida y vuelta conserva la sesion, la valoracion y la fecha de creacion`() {
        val prediction = PredictionSnapshot(now.minusSeconds(3600 * 12), brovales, false, 0.7, FavorabilityBand.FAVORABLE, null, null)
        val original = form.toSession(null, brovales, now).session!!.copy(id = 5, prediction = prediction, rodHoursOverride = 10.0, groundbaitKg = 1.5)
        val again = SessionForm.from(original)
        assertEquals("10", again.rodHours)
        assertEquals("1,5", again.groundbait)
        assertEquals(original, again.toSession(original, GeoPoint(0.0, 0.0), now.plusSeconds(999)).session)
    }

    @Test
    fun `las mediciones de la sesion van a Datos a la hora de inicio`() {
        val raw = form.copy(waterSurface = "18,4", turbidity = 2, reservoirPercent = "61").measurementRecord()!!
        assertEquals("2026-09-27 07:20", raw.time)
        assertEquals(SessionForm.MEASUREMENT_SOURCE, raw.source)
        assertEquals(setOf("temp_agua_superficie_c", "turbidez", "nivel_embalse_pct"), raw.values.keys)
        assertNull(form.measurementRecord())

        val start = Instant.parse("2026-09-27T05:20:00Z")
        val record = ManualRecord(
            period = RecordPeriod.At(start), location = brovales, source = SessionForm.MEASUREMENT_SOURCE, origin = ManualOrigin.Typed,
            values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.4, ManualField.TURBIDITY to 2.0), createdAt = start,
        )
        val other = record.copy(source = "termómetro", values = mapOf(ManualField.WATER_TEMP_SURFACE to 10.0))
        val filled = form.withMeasurementsFrom(listOf(other, record), start)
        assertEquals("18,4", filled.waterSurface)
        assertEquals(2, filled.turbidity)
        assertEquals("", filled.reservoirPercent)
    }

    @Test
    fun `sesion que empieza ahora queda en curso`() {
        val start = SessionForm.startingNow(Instant.parse("2026-10-03T16:31:40Z"))
        assertEquals("2026-10-03", start.date)
        assertEquals("18:31", start.startTime)
        val session = start.toSession(null, brovales, now).session!!
        assertTrue(session.isOngoing)
        assertEquals(LocalTime.of(7, 5), SessionForm.parseTime("7.05"))
        assertNull(SessionForm.parseTime("7"))
    }

    @Test
    fun `estado del diario con la sesion en curso y el resumen del mes`() {
        val finished = form.toSession(null, brovales, now).session!!.copy(id = 1)
        val ongoing = SessionForm.startingNow(now.plus(Duration.ofDays(6))).toSession(null, brovales, now).session!!.copy(id = 2)
        val state = buildDiaryState(listOf(finished, ongoing), Instant.parse("2026-10-03T10:00:00Z"))
        assertEquals(ongoing, state.ongoing)
        assertEquals(0, state.monthSummary.sessions) // la terminada es de septiembre y la de octubre sigue en curso
        assertEquals(1, state.yearSummary.sessions)
        assertEquals(1, state.recentCatchZones.size)
        assertEquals(1, legalWindowsFor(finished).size)
    }

    private val rules = RulesJson.parse(File("src/main/assets/rules.json").readText())

    private fun rawWithWater(at: Instant) = RawWeather(
        null, emptyList(), null,
        manualRecords = listOf(
            ManualRecord(
                period = RecordPeriod.At(at), location = brovales, source = "termómetro", origin = ManualOrigin.Typed,
                values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.0), createdAt = at,
            ),
        ),
    )

    @Test
    fun `una sesion que empieza ahora guarda la valoracion calculada en ese momento`() {
        val start = Instant.parse("2026-09-27T07:00:00Z")
        val session = Session(start = start, location = brovales, createdAt = start)
        val completed = completeSession(session, isNew = true, now = start.plusSeconds(30), raw = rawWithWater(start.minusSeconds(1800)), rules = rules, snapshots = emptyList())
        val prediction = completed.prediction!!
        assertEquals(start.plusSeconds(30), prediction.computedAt)
        assertNotNull(prediction.band)
        // Foto completa para la fase 7: parámetros, niveles, reglas activadas y huella de rules.json.
        assertEquals(18.0, prediction.features!!.numbers["temp_agua_c"])
        assertEquals(4, prediction.levels.size)
        assertTrue(prediction.activeRules.isNotEmpty())
        assertEquals(12, prediction.rulesFingerprint!!.length)
        assertEquals(18.0, completed.context!!.features!!.numbers["temp_agua_c"])
        assertEquals(18.0, completed.context?.waterTempC)
        assertTrue(completed.context!!.waterMeasured)
    }

    @Test
    fun `una sesion anotada despues usa la valoracion guardada antes, nunca una nueva`() {
        val start = Instant.parse("2026-09-27T05:20:00Z")
        val session = Session(start = start, end = start.plus(Duration.ofHours(5)), location = brovales, blank = true, createdAt = now)
        val before = PredictionSnapshot(start.minusSeconds(36_000), brovales, false, 0.4, FavorabilityBand.INTERMEDIATE, null, null)
        val completed = completeSession(session, isNew = true, now = now, raw = rawWithWater(start), rules = rules, snapshots = listOf(before))
        assertEquals(before, completed.prediction)
        val without = completeSession(session, isNew = true, now = now, raw = rawWithWater(start), rules = rules, snapshots = emptyList())
        assertNull(without.prediction)
        // Al editarla no se toca la copia fija.
        val edited = completeSession(completed.copy(id = 3), isNew = false, now = now, raw = null, rules = null, snapshots = emptyList())
        assertEquals(before, edited.prediction)
        assertFalse(edited.isOngoing)
    }
}
