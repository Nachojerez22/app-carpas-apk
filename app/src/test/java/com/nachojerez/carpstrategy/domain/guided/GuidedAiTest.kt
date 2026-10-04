package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.assistant.AiChange
import com.nachojerez.carpstrategy.domain.assistant.AiDecision
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedAiTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val t0 = Instant.parse("2026-10-04T07:00:00Z")
    private val gear = listOf(GearItem("1", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE))
    private fun env(min: Long) = GuidedEnv(t0.plus(Duration.ofMinutes(min)), gear, madrid)

    private fun started(): Session {
        val s = GuidedSessions.start(Session(start = t0, location = GeoPoint(38.35, -6.7), createdAt = t0), listOf("fija", "carrete"), null, env(0)).session
        return s.guided!!.rods.fold(s) { acc, rod -> GuidedSessions.accept(acc, rod.id, env(0)).session }
    }

    private fun exchange(valid: Boolean) = AiExchange(t0.plus(Duration.ofMinutes(30)), AiKind.CHECK_IN, "GEMINI", "m", valid = valid, issues = if (valid) emptyList() else listOf(AiIssue.GRAMS))

    private val decision = AiDecision(
        keep = false,
        changes = listOf(AiChange(2, StepKind.COLUMN, "Prueba a media agua", column = Column.POPUP, reason = "Saltos", evidence = Evidence.YELLOW)),
        reasons = listOf("Actividad arriba"),
        evidence = Evidence.YELLOW,
    )

    @Test
    fun `una decision valida queda como propuesta de la IA en su cana`() {
        val update = GuidedSessions.applyAi(started(), exchange(true), decision, env(30))
        val record = update.session.guided!!
        assertEquals(1, record.ai.size)
        val pending = record.rod(2)!!.log.pending!!.proposal
        assertEquals(ProposalSource.AI, pending.source)
        assertEquals(Column.POPUP, pending.column)
        assertEquals(pending, update.newProposals[2])
        assertNull(record.rod(1)!!.log.pending)
        // Aceptarla abre un tramo como cualquier otra.
        val accepted = GuidedSessions.accept(update.session, 2, env(31)).session
        assertEquals(StepKind.COLUMN, accepted.guided!!.rod(2)!!.log.current.kind)
    }

    @Test
    fun `una respuesta no valida solo se registra`() {
        val update = GuidedSessions.applyAi(started(), exchange(false), decision, env(30))
        assertEquals(1, update.session.guided!!.ai.size)
        assertTrue(update.session.guided!!.rods.all { it.log.pending == null })
        assertTrue(update.newProposals.isEmpty())
    }

    @Test
    fun `no pisa una propuesta pendiente`() {
        val first = GuidedSessions.applyAi(started(), exchange(true), decision, env(30)).session
        val other = decision.copy(changes = listOf(decision.changes.single().copy(kind = StepKind.DISTANCE)))
        val second = GuidedSessions.applyAi(first, exchange(true), other, env(35))
        assertEquals(StepKind.COLUMN, second.session.guided!!.rod(2)!!.log.pending!!.proposal.kind)
        assertEquals(2, second.session.guided!!.ai.size)
    }

    @Test
    fun `contexto para validar`() {
        val ctx = GuidedSessions.aiContext(started(), env(30), listOf(Spot(1, "Punta")))
        assertEquals(setOf(1, 2), ctx.rodIds)
        assertEquals(gear, ctx.gear)
        assertEquals(false, ctx.storm)
        assertTrue(ctx.legalEnd!!.isAfter(t0))
    }
}
