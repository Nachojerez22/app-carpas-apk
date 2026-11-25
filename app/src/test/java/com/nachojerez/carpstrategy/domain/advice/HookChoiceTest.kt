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
    fun `el anzuelo va con el cebo`() {
        assertEquals(14, choose(BaitType.MAIZE, RodKind.FIXED)!!.size)
        assertEquals(12, choose(BaitType.PASTE, RodKind.FIXED)!!.size)
        assertEquals(10, choose(BaitType.BOILIE, RodKind.CARP)!!.size)
        assertEquals(10, choose(BaitType.TIGERNUT, RodKind.CARP)!!.size)
        assertEquals(listOf(HookReason.BAIT), choose(BaitType.MAIZE, RodKind.FIXED)!!.reasons)
    }

    @Test
    fun `con agua fria un punto mas pequeno y en la fija hasta el 16`() {
        val c = choose(BaitType.MAIZE, RodKind.FIXED, waterC = 9.0)!!
        assertEquals(16, c.size)
        assertTrue(HookReason.COLD in c.reasons)
        // No baja al 18: no es anzuelo para la carpa de Brovales.
        assertNull(c.smaller)
        assertEquals(14, c.larger)
    }

    @Test
    fun `en carpfishing nunca mas pequeno que el 14`() {
        val c = choose(BaitType.MAIZE, RodKind.CARP, waterC = 9.0)!!
        assertEquals(14, c.size)
        assertTrue(HookReason.CARP_LIMIT in c.reasons)
        assertNull(c.smaller)
        assertFalse(HookChooser.allowed(16, RodKind.CARP))
        assertTrue(HookChooser.allowed(16, RodKind.FIXED))
        assertFalse(HookChooser.allowed(18, RodKind.FIXED))
        assertFalse(HookChooser.allowed(8, RodKind.CARP))
    }

    @Test
    fun `peces pequenos o agua caliente, mas grande, y si no lo tienes el mas cercano`() {
        assertEquals(12, choose(BaitType.MAIZE, RodKind.FIXED, smallFish = true)!!.size)
        val c = choose(BaitType.BOILIE, RodKind.CARP, waterC = 24.0)!!
        assertEquals(10, c.size)
        assertEquals(8, c.ideal)
        assertTrue(HookReason.WARM in c.reasons)
        assertTrue(HookReason.NOT_OWNED in c.reasons)
        assertEquals(12, c.smaller)
        assertNull(c.larger)
    }

    @Test
    fun `cebos que no van al anzuelo no llevan anzuelo`() {
        assertNull(choose(BaitType.PELLET, RodKind.CARP))
        assertEquals(12, choose(null, null)!!.size)
        assertEquals(0.16..0.18, HookChooser.leaderMm(14))
    }
}
