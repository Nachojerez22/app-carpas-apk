package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.assistant.AiGroundbait
import com.nachojerez.carpstrategy.domain.assistant.AiMixPart
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiRodPlan
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodRules
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.guided.today
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DayPlanTest {
    private val point = GeoPoint(38.35, -6.70)
    private val sun = SolarCalculator.sunTimes(LocalDate.of(2026, 7, 15), point)
    private val gear = listOf(
        GearItem("1", GearCategory.BAIT, "Boilie fresa", baitType = BaitType.BOILIE),
        GearItem("2", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT),
        GearItem("3", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE),
    )
    private val spots = listOf(
        Spot(1, "Orilla", structure = SpotStructure.SHALLOW_EDGE, facingDeg = 270, distanceM = 25.0),
        Spot(2, "Caída", structure = SpotStructure.FIRST_DROP, facingDeg = 90, distanceM = 60.0),
    )

    private fun advice(chosen: Long? = null) = FishingAdvisor.advise(
        AdvisorInput(
            sun.sunrise!!.plusSeconds(1800), FishingPhase.SUMMER, 24.0, sun, LegalWindow.of(sun),
            windFromDeg = 270.0, windKmh = 16.0, spots = spots, gear = gear, chosenSpotId = chosen,
        ),
    )

    @Test
    fun `plan de la app para el puesto elegido, un cebo distinto por cana y zig en la tercera`() {
        val plan = DayPlans.build(advice(chosen = 2), rods = 3, phase = FishingPhase.SUMMER, gear = gear)
        assertEquals("Caída", plan.spot?.spot?.name)
        assertEquals(PlanSource.APP, plan.source)
        assertEquals(listOf(1, 2, 3), plan.rods.map { it.rod })
        assertTrue(plan.rods.all { it.spotName == "Caída" && it.distanceM == 60.0 })
        assertEquals(listOf("Boilie fresa", "Maíz", "Boilie fresa"), plan.rods.map { it.baitName })
        assertEquals(listOf(Column.BOTTOM, Column.BOTTOM, Column.ZIG), plan.rods.map { it.column })
        assertNull(plan.aiSummary)
    }

    @Test
    fun `en automatico el plan es para el mejor puesto y las canas se limitan a 3`() {
        val plan = DayPlans.build(advice(), rods = 5, phase = FishingPhase.SUMMER, gear = gear)
        assertEquals("Orilla", plan.spot?.spot?.name)
        assertEquals(3, plan.rods.size)
        assertEquals(1, DayPlans.build(advice(), rods = 0, phase = FishingPhase.SUMMER, gear = gear).rods.size)
    }

    @Test
    fun `la IA afina cana a cana y la app rellena lo que falta`() {
        val ai = AiPlan(
            "Empieza con chufa en la caída", Evidence.YELLOW,
            listOf(AiRodPlan(1, "Caída", "chufa", null, Column.POPUP, 55.0, "Fondo duro en la caída", Evidence.PURPLE)),
        )
        val plan = DayPlans.build(advice(chosen = 2), rods = 2, phase = FishingPhase.SUMMER, gear = gear, ai = ai)
        val r1 = plan.rods[0]
        assertEquals(PlanSource.AI, r1.source)
        assertEquals(BaitType.TIGERNUT, r1.baitType)
        assertEquals(Column.POPUP, r1.column)
        assertEquals(55.0, r1.distanceM)
        assertEquals("Fondo duro en la caída", r1.reason)
        assertEquals(Evidence.PURPLE, r1.evidence)
        // La caña 2 no la cita la IA: se queda la de la app.
        assertEquals(PlanSource.APP, plan.rods[1].source)
        assertEquals(PlanSource.AI, plan.source)
        assertEquals("Empieza con chufa en la caída", plan.aiSummary)
    }

    @Test
    fun `que hacer si pasa algo con los tiempos de la fase y los avisos del viento`() {
        val a = advice(chosen = 2)
        val list = DayPlans.contingencies(a, FishingPhase.SUMMER, a.chosen)
        val th = GuidedEngine.THRESHOLDS.getValue(FishingPhase.SUMMER)
        assertEquals(Contingency.NO_SIGNALS, list[0].kind)
        assertEquals(th.step1, list[0].after)
        assertEquals(StepKind.COLUMN, list[0].step)
        assertEquals(th.step2, list.first { it.kind == Contingency.NO_SIGNALS_ZONE }.after)
        assertTrue(list.any { it.kind == Contingency.CARP_UP })
        // La caída mira al E y el viento viene del O: de espaldas → cebar lejos.
        assertTrue(list.any { it.kind == Contingency.WIND && it.windTip == WindTip.BAIT_FAR })
    }

    @Test
    fun `cebado en kilos con tu medida y el engodo de la IA`() {
        val a = advice(chosen = 2)
        val plan = DayPlans.build(a, rods = 2, phase = FishingPhase.SUMMER, gear = gear, groundbaitNormalKg = 1.0)
        // Verano al amanecer: mucho (2×).
        assertEquals(2.0, plan.groundbaitAmount!!.perRodKg)
        assertEquals(4.0, plan.groundbaitAmount!!.totalKg)
        assertNull(DayPlans.build(a, rods = 2, phase = FishingPhase.SUMMER, gear = gear).groundbaitAmount)
        val ai = AiPlan("Plan", Evidence.YELLOW, emptyList(), groundbait = AiGroundbait(listOf(AiMixPart("Maíz", 1)), perRodKg = 1.5, evidence = Evidence.YELLOW))
        val withAi = DayPlans.build(a, rods = 2, phase = FishingPhase.SUMMER, gear = gear, ai = ai, groundbaitNormalKg = 1.0)
        assertEquals(1.5, withAi.groundbaitAmount!!.perRodKg)
        assertEquals(0.75, withAi.groundbaitAmount!!.initialPerRodKg)
        assertEquals("Maíz", withAi.aiGroundbait!!.mix.single().baitName)
    }

    @Test
    fun `solo lo que llevas hoy`() {
        val list = listOf(gear[0], gear[1].copy(available = false))
        assertEquals(listOf("Boilie fresa"), list.today().map { it.name })
    }

    @Test
    fun `con canas de mi equipo cada linea respeta su tipo`() {
        val setups = listOf(RodSetup("Larga", RodKind.FIXED, 7), RodSetup("Carrete", RodKind.CARP), RodSetup("Otra", RodKind.CARP))
        val plan = DayPlans.build(advice(chosen = 2), rods = 1, phase = FishingPhase.SUMMER, gear = gear, setups = setups)
        assertEquals(3, plan.rods.size)
        val fixed = plan.rods[0]
        assertEquals("Larga", fixed.setup?.name)
        assertTrue(RodRules.baitAllowed(RodKind.FIXED, fixed.baitType))
        assertEquals(7.0, fixed.distanceM)
        assertEquals(3..4, fixed.weightRange)
        val carp = plan.rods[2]
        // La 3.ª caña probaría zig, pero en carpfishing se pesca siempre a fondo.
        assertEquals(Column.BOTTOM, carp.column)
        assertEquals(60.0, carp.distanceM)
        assertEquals(40..90, carp.weightRange)
    }
}
