package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.RodKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Qué anzuelo montar (CONOCIMIENTO.md §5.9.15), con los anzuelos del usuario (18 · 16 · 14 · 12 · 10). */
class HookChoiceTest {
    private fun choose(bait: BaitType?, kind: RodKind?, waterC: Double? = 16.0, smallFish: Boolean = false) =
        HookChooser.choose(HookInput(bait, kind, waterC, smallFish))

    @Test
    fun `manda la actividad, menos activas anzuelo mas pequeno`() {
        assertEquals(16, choose(BaitType.MAIZE, RodKind.FIXED, waterC = 9.0)!!.size)
        assertEquals(14, choose(BaitType.MAIZE, RodKind.FIXED, waterC = 15.0)!!.size)
        assertEquals(12, choose(BaitType.MAIZE, RodKind.FIXED, waterC = 24.0)!!.size)
        assertEquals(14, choose(BaitType.BOILIE, RodKind.CARP, waterC = 9.0)!!.size)
        assertEquals(12, choose(BaitType.BOILIE, RodKind.CARP, waterC = 15.0)!!.size)
        assertEquals(10, choose(BaitType.BOILIE, RodKind.CARP, waterC = 24.0)!!.size)
        assertEquals(listOf(HookReason.ACTIVITY_LOW), choose(BaitType.MAIZE, RodKind.FIXED, waterC = 9.0)!!.reasons)
    }

    @Test
    fun `el mismo cebo con distinta actividad cambia de anzuelo, y distinto cebo con la misma no`() {
        assertEquals(choose(BaitType.MAIZE, RodKind.CARP)!!.size, choose(BaitType.TIGERNUT, RodKind.CARP)!!.size)
        assertTrue(choose(BaitType.MAIZE, RodKind.CARP, waterC = 9.0)!!.size > choose(BaitType.MAIZE, RodKind.CARP, waterC = 24.0)!!.size)
    }

    @Test
    fun `el cebo solo limita, que quepa con la punta libre`() {
        val paste = choose(BaitType.PASTE, RodKind.FIXED, waterC = 9.0)!!
        assertEquals(14, paste.size)
        assertTrue(HookReason.BAIT_FIT in paste.reasons)
        assertEquals(12, choose(BaitType.TIGERNUT, RodKind.FIXED, waterC = 15.0)!!.size)
    }

    @Test
    fun `limites de cada cana`() {
        val fixed = choose(BaitType.MAIZE, RodKind.FIXED, waterC = 9.0)!!
        // No baja al 18: no es anzuelo para la carpa de Brovales.
        assertNull(fixed.smaller)
        assertEquals(14, fixed.larger)
        assertNull(choose(BaitType.MAIZE, RodKind.CARP, waterC = 9.0)!!.smaller)
        assertFalse(HookChooser.allowed(16, RodKind.CARP))
        assertTrue(HookChooser.allowed(16, RodKind.FIXED))
        assertFalse(HookChooser.allowed(18, RodKind.FIXED))
        assertFalse(HookChooser.allowed(8, RodKind.CARP))
    }

    @Test
    fun `peces pequenos mas grande, y si no lo tienes el mas cercano`() {
        assertEquals(12, choose(BaitType.MAIZE, RodKind.FIXED, smallFish = true)!!.size)
        val c = choose(BaitType.BOILIE, RodKind.CARP, waterC = 24.0, smallFish = true)!!
        assertEquals(10, c.size)
        assertEquals(8, c.ideal)
        assertTrue(HookReason.NOT_OWNED in c.reasons)
        assertEquals(12, c.smaller)
        assertNull(c.larger)
    }

    @Test
    fun `sin agua actividad media y cebos que no van al anzuelo sin anzuelo`() {
        val c = choose(BaitType.MAIZE, RodKind.FIXED, waterC = null)!!
        assertEquals(14, c.size)
        assertEquals(listOf(HookReason.ACTIVITY_UNKNOWN), c.reasons)
        assertNull(choose(BaitType.PELLET, RodKind.CARP))
        assertEquals(0.16..0.18, HookChooser.leaderMm(14))
    }
}
