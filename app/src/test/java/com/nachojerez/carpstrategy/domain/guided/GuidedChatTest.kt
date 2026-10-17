package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.data.userdata.GuidedJson
import com.nachojerez.carpstrategy.domain.assistant.AiChange
import com.nachojerez.carpstrategy.domain.assistant.AiChatReply
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiValidator
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedChatTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val t0 = Instant.parse("2026-10-17T08:00:00Z")
    private val gear = listOf(GearItem("1", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE))
    private fun env(min: Long) = GuidedEnv(t0.plus(Duration.ofMinutes(min)), gear, madrid)

    private fun started(): Session {
        val s = GuidedSessions.start(Session(start = t0, location = GeoPoint(38.35, -6.7), createdAt = t0), listOf("fija", "carrete"), null, env(0)).session
        return s.guided!!.rods.fold(s) { acc, rod -> GuidedSessions.accept(acc, rod.id, env(0)).session }
    }

    private fun exchange(valid: Boolean, error: String? = null) =
        AiExchange(t0.plus(Duration.ofMinutes(20)), AiKind.CHAT, "GEMINI", "m", valid = valid, error = error)

    private val reply = AiChatReply(
        "Con saltos arriba, prueba un pop-up en la carrete.",
        listOf(AiChange(2, StepKind.COLUMN, "Pop-up a media agua", column = Column.POPUP, reason = "Saltos", evidence = Evidence.YELLOW)),
        Evidence.YELLOW,
    )

    @Test
    fun `pregunta y respuesta quedan en la sesion y el cambio como propuesta`() {
        val update = GuidedSessions.applyChat(started(), "Hay carpas saltando a 40 m", t0.plus(Duration.ofMinutes(19)), exchange(true), reply, env(20))
        val record = update.session.guided!!
        assertEquals(2, record.chat.size)
        assertTrue(record.chat[0].fromUser)
        assertEquals("Hay carpas saltando a 40 m", record.chat[0].text)
        assertEquals(reply.text, record.chat[1].text)
        assertEquals(listOf(2), record.chat[1].proposedRods)
        assertEquals(ProposalSource.AI, record.rod(2)!!.log.pending!!.proposal.source)
        assertEquals(AiKind.CHAT, record.ai.single().kind)
        // Se guarda y se lee igual.
        assertEquals(record.chat, GuidedJson.decode(GuidedJson.encode(record))!!.chat)
    }

    @Test
    fun `sin respuesta valida se guarda la pregunta y no cambia nada`() {
        val update = GuidedSessions.applyChat(started(), "¿Cambio de cebo?", t0, exchange(false, error = "Offline"), null, env(20))
        val record = update.session.guided!!
        assertEquals(2, record.chat.size)
        assertTrue(record.chat[1].rejected)
        assertTrue(record.rods.all { it.log.pending == null })
    }

    @Test
    fun `validacion de la respuesta del chat`() {
        val ctx = GuidedSessions.aiContext(started(), env(20), emptyList())
        assertTrue(AiValidator.validate(reply, ctx).isEmpty())
        assertTrue(AiIssue.UNKNOWN_ROD in AiValidator.validate(reply.copy(changes = listOf(reply.changes.single().copy(rod = 7))), ctx))
        assertTrue(AiIssue.GRAMS in AiValidator.validate(AiChatReply("Echa 500 g de maíz"), ctx))
        assertTrue(AiIssue.NIGHT in AiValidator.validate(AiChatReply("Quédate de noche"), ctx))
        // Un texto más largo que un motivo vale en el chat.
        assertFalse(AiIssue.TEXT_TOO_LONG in AiValidator.validate(AiChatReply("a".repeat(900)), ctx))
        assertTrue(AiIssue.TEXT_TOO_LONG in AiValidator.validate(AiChatReply("a".repeat(1_300)), ctx))
    }
}
