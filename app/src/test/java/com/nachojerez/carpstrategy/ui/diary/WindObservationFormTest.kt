package com.nachojerez.carpstrategy.ui.diary

import com.nachojerez.carpstrategy.data.userdata.GuidedJson
import com.nachojerez.carpstrategy.data.userdata.JournalJson
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.WindRelation
import com.nachojerez.carpstrategy.domain.journal.PracticalIssue
import com.nachojerez.carpstrategy.domain.journal.SessionContext
import com.nachojerez.carpstrategy.domain.journal.WaveLevel
import com.nachojerez.carpstrategy.domain.journal.WindObservation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.ui.guided.SpotDraft
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindObservationFormTest {
    private val here = GeoPoint(38.35, -6.70)
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val form = SessionForm(
        date = "2026-10-24",
        startTime = "08:00",
        endTime = "11:00",
        windRelation = WindRelation.FACING,
        waves = WaveLevel.SMALL_WAVES,
        foamLine = true,
        airShore = "18,5",
        issues = setOf(PracticalIssue.FALSE_BEEPS, PracticalIssue.SHORT_CAST),
    )

    @Test
    fun `lo observado va al contexto y vuelve al formulario`() {
        val session = form.toSession(null, here, now).session!!
        val observed = session.context!!.observed!!
        assertEquals(WindRelation.FACING, observed.relation)
        assertEquals(18.5, observed.airShoreC)
        assertEquals(setOf(PracticalIssue.FALSE_BEEPS, PracticalIssue.SHORT_CAST), observed.issues)
        val back = SessionForm.from(session)
        assertEquals(form.waves, back.waves)
        assertEquals("18,5", back.airShore)
        assertEquals(form.issues, back.issues)
        // Se guarda y se lee igual (contexto de la BD y exportación).
        assertEquals(observed, JournalJson.decodeContext(JournalJson.encodeContext(session.context!!))!!.observed)
    }

    @Test
    fun `aire fuera de rango es un error y sin observacion no se crea contexto`() {
        assertTrue(FormField.AIR_SHORE in form.copy(airShore = "80").toSession(null, here, now).fieldErrors)
        val plain = SessionForm(date = "2026-10-24", startTime = "08:00").toSession(null, here, now).session!!
        assertNull(plain.context)
        // Quitar lo observado no borra el contexto calculado.
        val calculated = SessionContext(waterTempC = 17.0, observed = WindObservation(waves = WaveLevel.FLAT))
        assertEquals(SessionContext(waterTempC = 17.0), SessionForm.withObservation(calculated, WindObservation()))
    }

    @Test
    fun `el fetch del puesto se guarda`() {
        val spot = Spot(1, "Punta", facingDeg = 225, fetchM = 900.0)
        assertEquals(900.0, GuidedJson.decodeSpots(GuidedJson.encodeSpots(listOf(spot))).single().fetchM)
        assertEquals(900.0, SpotDraft.of(spot).toSpot { 1 }.fetchM)
    }
}

