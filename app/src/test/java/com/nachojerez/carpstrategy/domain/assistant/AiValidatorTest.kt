package com.nachojerez.carpstrategy.domain.assistant

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.ProposalSource
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.Situation
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiValidatorTest {
    private val now = Instant.parse("2026-10-04T09:00:00Z")
    private val gear = listOf(
        GearItem("1", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT),
        GearItem("2", GearCategory.RIG, "Pop-up 2 cm", rigType = RigType.POPUP),
    )
    private val ctx = AiContext(now, now.plus(Duration.ofHours(8)), setOf(1, 2), gear, listOf(Spot(1, "Punta")))

    private val change = AiChange(1, StepKind.COLUMN, "Sube el cebo con el pop-up", rigName = "pop-up 2 CM", reason = "Señales a media agua", evidence = Evidence.YELLOW)
    private val decision = AiDecision(false, listOf(change), listOf("Hay señales pero no picadas"), Evidence.YELLOW)

    @Test
    fun `respuesta correcta pasa`() {
        assertTrue(AiValidator.validate(decision, ctx).isEmpty())
        assertTrue(AiValidator.validate(AiDecision(true, emptyList(), listOf("Paciencia tras la captura"), Evidence.GREEN), ctx).isEmpty())
    }

    @Test
    fun `sin probabilidades, gramos, noche ni promesas`() {
        assertEquals(setOf(AiIssue.PROBABILITY), AiValidator.textIssues("Un 70 % de opciones"))
        assertEquals(setOf(AiIssue.PROBABILITY), AiValidator.textIssues("alta probabilidad de picada"))
        assertEquals(setOf(AiIssue.GRAMS), AiValidator.textIssues("Ceba 500 g de maíz"))
        assertEquals(setOf(AiIssue.GRAMS), AiValidator.textIssues("ceba 1,5 kg"))
        assertEquals(setOf(AiIssue.NIGHT), AiValidator.textIssues("Quédate por la noche"))
        assertEquals(setOf(AiIssue.PROMISE), AiValidator.textIssues("Te garantizo capturas"))
        assertTrue(AiValidator.textIssues("Prueba al anochecer legal, a 40 m, poco cebado").isEmpty())
        assertEquals(setOf(AiIssue.EMPTY_TEXT), AiValidator.textIssues(" "))
        assertEquals(setOf(AiIssue.TEXT_TOO_LONG), AiValidator.textIssues("a".repeat(AiValidator.MAX_TEXT + 1)))
    }

    @Test
    fun `solo tu equipo, tus puestos y tus canas`() {
        val bad = decision.copy(changes = listOf(change.copy(rod = 3, baitName = "Boilie de piña", rigName = "Zig", spotName = "Presa")))
        assertEquals(setOf(AiIssue.UNKNOWN_ROD, AiIssue.UNKNOWN_BAIT, AiIssue.UNKNOWN_RIG, AiIssue.UNKNOWN_SPOT), AiValidator.validate(bad, ctx))
        val twice = decision.copy(changes = listOf(change, change.copy(kind = StepKind.DISTANCE)))
        assertEquals(setOf(AiIssue.DUPLICATE_ROD), AiValidator.validate(twice, ctx))
    }

    @Test
    fun `decision incoherente, paso no permitido y evidencia obligatoria`() {
        assertTrue(AiIssue.UNKNOWN_DECISION in AiValidator.validate(decision.copy(keep = null), ctx))
        assertTrue(AiIssue.CHANGES_WHEN_KEEPING in AiValidator.validate(decision.copy(keep = true), ctx))
        assertTrue(AiIssue.NO_CHANGES in AiValidator.validate(decision.copy(changes = emptyList()), ctx))
        assertTrue(AiIssue.KIND_NOT_ALLOWED in AiValidator.validate(decision.copy(changes = listOf(change.copy(kind = StepKind.INITIAL))), ctx))
        assertTrue(AiIssue.KIND_NOT_ALLOWED in AiValidator.validate(decision.copy(changes = listOf(change.copy(kind = null))), ctx))
        assertTrue(AiIssue.MISSING_EVIDENCE in AiValidator.validate(decision.copy(evidence = null), ctx))
        assertTrue(AiIssue.MISSING_EVIDENCE in AiValidator.validate(decision.copy(changes = listOf(change.copy(evidence = null))), ctx))
        assertTrue(AiIssue.EMPTY_TEXT in AiValidator.validate(decision.copy(reasons = emptyList()), ctx))
    }

    @Test
    fun `horario legal, limite de zonas y tormenta`() {
        val zone = decision.copy(changes = listOf(change.copy(kind = StepKind.ZONE, spotName = "punta")))
        assertTrue(AiValidator.validate(zone, ctx).isEmpty())
        val late = ctx.copy(legalEnd = now.plus(Duration.ofMinutes(40)))
        assertEquals(setOf(AiIssue.ZONE_NOT_ALLOWED), AiValidator.validate(zone, late))
        assertTrue(AiValidator.validate(decision, late).isEmpty())
        assertEquals(setOf(AiIssue.ZONE_NOT_ALLOWED), AiValidator.validate(zone, ctx.copy(zoneLimitReached = setOf(1))))
        assertTrue(AiIssue.AFTER_LEGAL_END in AiValidator.validate(decision, ctx.copy(legalEnd = now.minusSeconds(60))))
        assertTrue(AiIssue.STORM_ACTIVE in AiValidator.validate(decision, ctx.copy(storm = true)))
        // Mantener siempre se puede (también con tormenta).
        assertTrue(AiValidator.validate(AiDecision(true, emptyList(), listOf("Ponte a cubierto"), Evidence.GREEN), ctx.copy(storm = true)).isEmpty())
    }

    @Test
    fun `plan valido y plan con problemas`() {
        val plan = AiPlan(
            "Condiciones intermedias: empieza en la punta",
            Evidence.YELLOW,
            listOf(AiRodPlan(1, "Punta", "Chufa", null, Column.BOTTOM, 40.0, "Fondo duro", Evidence.PURPLE)),
            listOf(AiNote("Recoge 1 h después del ocaso", Evidence.REGULATION)),
        )
        assertTrue(AiValidator.validate(plan, ctx).isEmpty())
        val bad = plan.copy(
            evidence = null,
            rods = listOf(AiRodPlan(5, distanceM = 400.0, reason = "Ceba 2 kg", evidence = Evidence.YELLOW)),
        )
        assertEquals(
            setOf(AiIssue.MISSING_EVIDENCE, AiIssue.UNKNOWN_ROD, AiIssue.DISTANCE_OUT_OF_RANGE, AiIssue.GRAMS),
            AiValidator.validate(bad, ctx),
        )
    }

    @Test
    fun `un cambio valido se convierte en propuesta de la IA con tu equipo`() {
        val p = AiValidator.toProposal(change.copy(baitName = "chufa"), gear, now)
        assertEquals(StepKind.COLUMN, p.kind)
        assertEquals(Situation.ASSISTANT, p.situation)
        assertEquals(ProposalSource.AI, p.source)
        assertEquals(BaitType.TIGERNUT, p.bait)
        assertEquals("Chufa", p.baitName)
        assertEquals("Pop-up 2 cm", p.rigName)
        assertEquals(Column.POPUP, p.column)
        assertEquals("Sube el cebo con el pop-up — Señales a media agua", p.note)
        assertEquals(Evidence.YELLOW, p.evidence)
    }
}
