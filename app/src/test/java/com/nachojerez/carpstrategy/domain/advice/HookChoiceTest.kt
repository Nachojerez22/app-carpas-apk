package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.toRodSetup
import com.nachojerez.carpstrategy.domain.reservoir.CarpWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Qué anzuelo montar (CONOCIMIENTO.md §5.9.15): caña y peso ponen el suelo, la actividad mueve, el cebo limita. */
class HookChoiceTest {
    private fun choose(
        bait: BaitType? = BaitType.MAIZE,
        profile: HookProfile? = HookProfile.FIXED_RIGID,
        waterC: Double? = 15.0,
        windKmh: Double? = null,
        weight: CarpWeight? = null,
        smallFish: Boolean = false,
        sizes: List<Int> = HookChooser.SIZES,
    ) = HookChooser.choose(HookInput(bait, profile, waterC, windKmh, weight, smallFish), sizes)!!

    @Test
    fun `manda la actividad dentro de lo que aguanta la cana`() {
        // Fija rígida sin peso en la ficha (se suponen 5–10 kg): 14 · 12 · 10.
        assertEquals(14, choose(waterC = 9.0).size)
        assertEquals(12, choose(waterC = 15.0).size)
        assertEquals(10, choose(waterC = 24.0).size)
        assertEquals(listOf(HookReason.ACTIVITY_LOW, HookReason.WEIGHT_ASSUMED), choose(waterC = 9.0).reasons)
        assertEquals(14, choose().floor)
    }

    @Test
    fun `la fija con elastico y carpas pequenas baja hasta el 18`() {
        val c = choose(profile = HookProfile.FIXED_ELASTIC, waterC = 9.0, weight = CarpWeight.FROM_2_TO_5)
        assertEquals(18, c.size)
        assertNull(c.smaller)
        assertEquals(16, c.larger)
        assertEquals(8..10, c.elasticGrade)
        assertTrue(HookReason.WEIGHT in c.reasons)
        assertNull(choose().elasticGrade)
    }

    @Test
    fun `con mas de 26 grados vuelve a bajar, y mas con calma`() {
        val calm = choose(waterC = 28.0, windKmh = 3.0)
        assertEquals(14, calm.size)
        assertTrue(HookReason.HEAT_CALM in calm.reasons)
        val wind = choose(waterC = 28.0, windKmh = 12.0)
        assertEquals(12, wind.size)
        assertTrue(HookReason.HEAT in wind.reasons)
        assertEquals(CarpActivity.HIGH, HookChooser.activity(26.0))
    }

    @Test
    fun `el cebo solo obliga a uno mas grande si no cabe`() {
        val paste = choose(BaitType.PASTE, HookProfile.FIXED_ELASTIC, waterC = 9.0, weight = CarpWeight.FROM_2_TO_5)
        assertEquals(14, paste.size)
        assertTrue(HookReason.BAIT_FIT in paste.reasons)
        assertNull(HookChooser.smallestForBait(BaitType.MAIZE, HookProfile.CARP_LEAD))
    }

    @Test
    fun `con peces pequenos un punto mas grande`() {
        val c = choose(smallFish = true)
        assertEquals(10, c.size)
        assertTrue(HookReason.SMALL_FISH in c.reasons)
    }

    @Test
    fun `sin temperatura del agua actividad media`() {
        assertEquals(listOf(HookReason.ACTIVITY_UNKNOWN, HookReason.WEIGHT_ASSUMED), choose(waterC = null).reasons)
    }

    @Test
    fun `si no tienes el ideal, el mas cercano de los tuyos que aguanta`() {
        val c = choose(profile = HookProfile.CARP_LEAD, waterC = 20.0)
        assertEquals(8, c.ideal)
        assertEquals(10, c.size)
        assertTrue(HookReason.NOT_OWNED in c.reasons)
        assertEquals(8, choose(profile = HookProfile.CARP_LEAD, waterC = 20.0, sizes = listOf(14, 12, 10, 8, 6)).size)
    }

    @Test
    fun `carpas grandes sin anzuelo fuerte avisa de que falta`() {
        val c = choose(profile = HookProfile.CARP_LEAD, waterC = 20.0, weight = CarpWeight.FROM_10_TO_15)
        assertEquals(10, c.size)
        assertTrue(HookReason.MISSING_STRONGER in c.reasons)
        assertEquals(6, choose(profile = HookProfile.CARP_LEAD, waterC = 20.0, weight = CarpWeight.FROM_10_TO_15, sizes = listOf(12, 10, 8, 6)).size)
    }

    @Test
    fun `avisos de cana al limite`() {
        assertTrue(HookReason.NOT_ADVISED in choose(weight = CarpWeight.FROM_10_TO_15).reasons)
        assertTrue(HookReason.AT_LIMIT in choose(profile = HookProfile.FIXED_ELASTIC, weight = CarpWeight.OVER_15).reasons)
    }

    @Test
    fun `lo que admite cada cana y lo que no va al anzuelo`() {
        assertFalse(HookChooser.allowed(16, HookProfile.FIXED_RIGID))
        assertTrue(HookChooser.allowed(14, HookProfile.FIXED_RIGID))
        assertTrue(HookChooser.allowed(18, HookProfile.FIXED_ELASTIC, CarpWeight.UNDER_2))
        assertFalse(HookChooser.allowed(12, HookProfile.CARP_LEAD))
        assertFalse(HookChooser.allowed(8, HookProfile.CARP_LEAD))
        assertTrue(HookChooser.allowed(8, HookProfile.CARP_LEAD, sizes = HookChooser.SIZES + 8))
        assertNull(HookChooser.choose(HookInput(BaitType.GROUNDBAIT_MIX, HookProfile.CARP_LEAD, 15.0)))
        assertEquals(0.17..0.19, HookChooser.leaderMm(14))
    }

    @Test
    fun `perfil de cada cana y anzuelos apuntados`() {
        assertEquals(HookProfile.FIXED_RIGID, RodSetup("Fija", RodKind.FIXED, 7).hookProfile())
        assertEquals(HookProfile.CARP_LEAD, RodSetup("Carrete", RodKind.CARP).hookProfile())
        assertEquals(HookProfile.CARP_FEEDER, RodSetup("Carrete", RodKind.CARP, feeder = true).hookProfile())
        val nine = GearItem("1", GearCategory.ROD, "Larga", rodKind = RodKind.FIXED, rodLengthM = 9)
        assertEquals(HookProfile.FIXED_ELASTIC, nine.toRodSetup().hookProfile())
        assertEquals(HookProfile.FIXED_RIGID, nine.copy(rodElastic = false).toRodSetup().hookProfile())
        assertEquals(HookChooser.SIZES, emptyList<GearItem>().hookSizes())
        val hooks = listOf(6, 8).map { GearItem("h$it", GearCategory.HOOK, "Anzuelo del $it", hookSize = it) }
        assertEquals(listOf(8, 6), hooks.hookSizes())
    }
}
