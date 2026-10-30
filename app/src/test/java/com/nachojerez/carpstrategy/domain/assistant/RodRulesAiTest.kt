package com.nachojerez.carpstrategy.domain.assistant

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** La IA respeta el tipo de caña (fase 7.20) y puede citar flotador y plomo en gramos dentro del rango. */
class RodRulesAiTest {
    private val now = Instant.parse("2026-10-04T09:00:00Z")
    private val gear = listOf(
        GearItem("1", GearCategory.BAIT, "Boilie fresa", baitType = BaitType.BOILIE),
        GearItem("2", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE),
        GearItem("3", GearCategory.RIG, "Zig 1 m", rigType = RigType.ZIG),
    )
    private val ctx = AiContext(
        now, now.plus(Duration.ofHours(8)), setOf(1, 2), gear, listOf(Spot(1, "Punta")),
        rodSetups = mapOf(1 to RodSetup("Fija", RodKind.FIXED, 7), 2 to RodSetup("Carrete", RodKind.CARP)),
    )

    private fun plan(vararg rods: AiRodPlan) = AiPlan("Empieza en la punta", Evidence.YELLOW, rods.toList())

    @Test
    fun `plan que respeta cada cana pasa`() {
        val ok = plan(
            AiRodPlan(1, baitName = "Maíz", column = Column.ZIG, distanceM = 6.0, weightG = 3.0, reason = "Flotador de 3 g a media agua", evidence = Evidence.PURPLE),
            AiRodPlan(2, baitName = "Boilie fresa", column = Column.BOTTOM, distanceM = 60.0, weightG = 80.0, reason = "Plomo de 80 g en la caída", evidence = Evidence.YELLOW),
        )
        assertTrue(AiValidator.validate(ok, ctx).isEmpty())
    }

    @Test
    fun `boilies en la fija o carpfishing fuera del fondo se rechazan`() {
        assertEquals(setOf(AiIssue.ROD_RULES), AiValidator.validate(plan(AiRodPlan(1, baitName = "Boilie fresa", reason = "a fondo", evidence = Evidence.YELLOW)), ctx))
        assertEquals(setOf(AiIssue.ROD_RULES), AiValidator.validate(plan(AiRodPlan(2, column = Column.ZIG, reason = "arriba", evidence = Evidence.YELLOW)), ctx))
        assertEquals(setOf(AiIssue.ROD_RULES), AiValidator.validate(plan(AiRodPlan(2, rigName = "Zig 1 m", reason = "arriba", evidence = Evidence.YELLOW)), ctx))
        // También en los cambios de la sesión guiada.
        val change = AiChange(1, StepKind.PRESENTATION, "Pon el boilie", baitName = "Boilie fresa", reason = "más selectivo", evidence = Evidence.YELLOW)
        assertTrue(AiIssue.ROD_RULES in AiValidator.validate(AiDecision(false, listOf(change), listOf("Cambia"), Evidence.YELLOW), ctx))
    }

    @Test
    fun `distancia y peso dentro del rango de cada cana`() {
        assertEquals(setOf(AiIssue.DISTANCE_OUT_OF_RANGE), AiValidator.validate(plan(AiRodPlan(1, distanceM = 12.0, reason = "lejos", evidence = Evidence.YELLOW)), ctx))
        assertEquals(setOf(AiIssue.DISTANCE_OUT_OF_RANGE), AiValidator.validate(plan(AiRodPlan(2, distanceM = 120.0, reason = "lejos", evidence = Evidence.YELLOW)), ctx))
        assertEquals(setOf(AiIssue.WEIGHT_OUT_OF_RANGE), AiValidator.validate(plan(AiRodPlan(1, weightG = 2.0, reason = "fino", evidence = Evidence.YELLOW)), ctx))
        assertEquals(setOf(AiIssue.WEIGHT_OUT_OF_RANGE), AiValidator.validate(plan(AiRodPlan(2, weightG = 120.0, reason = "pesado", evidence = Evidence.YELLOW)), ctx))
    }

    @Test
    fun `gramos de flotador y plomo en rango si, de cebo no`() {
        assertTrue(AiValidator.textIssues("Flotador de 3 g a media agua").isEmpty())
        assertTrue(AiValidator.textIssues("Plomo de 40 a 60 g y lance corto").isEmpty())
        assertTrue(AiValidator.textIssues("Pon 90 g de plomo").isEmpty())
        assertEquals(setOf(AiIssue.WEIGHT_OUT_OF_RANGE), AiValidator.textIssues("Flotador de 2 g"))
        assertEquals(setOf(AiIssue.WEIGHT_OUT_OF_RANGE), AiValidator.textIssues("Plomo de 120 g"))
        assertEquals(setOf(AiIssue.GRAMS), AiValidator.textIssues("Plomo de 60 g y ceba 500 g de maíz"))
    }
}
