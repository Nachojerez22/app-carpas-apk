package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotDraftTest {
    @Test
    fun `numeros con coma o punto y campos vacios`() {
        val draft = SpotDraft(name = " Punta ", depth = "3,5", distance = "40", facingDeg = 270, notes = " fondo duro ")
        assertTrue(draft.isValid)
        assertEquals(Spot(9, "Punta", null, SpotStructure.OTHER, 3.5, 40.0, 270, "fondo duro"), draft.toSpot { 9 })
        assertTrue(SpotDraft(name = "a", depth = "2.25").isValid)
        assertTrue(SpotDraft(name = "a").isValid)
    }

    @Test
    fun `invalido sin nombre o con numeros fuera de rango`() {
        assertFalse(SpotDraft(name = " ").isValid)
        assertFalse(SpotDraft(name = "a", depth = "x").isValid)
        assertFalse(SpotDraft(name = "a", depth = "-1").isValid)
        assertFalse(SpotDraft(name = "a", distance = "400").isValid)
    }

    @Test
    fun `editar conserva el id y vuelve a formatear`() {
        val spot = Spot(4, "Recula", FishingZone.NORTH, SpotStructure.INLET_BAY, 1.5, 30.0, 0, "")
        val draft = SpotDraft.of(spot)
        assertEquals("1,5", draft.depth)
        assertEquals("30", draft.distance)
        assertEquals(spot, draft.toSpot { error("no debe pedir id") })
        val list = listOf(spot, Spot(5, "Llano"))
        assertEquals(listOf(spot.copy(name = "Recula N"), Spot(5, "Llano")), list.upsertSpot(spot.copy(name = "Recula N")))
        assertEquals(3, list.upsertSpot(Spot(6, "Nuevo")).size)
    }
}
