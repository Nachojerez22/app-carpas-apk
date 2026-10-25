package com.nachojerez.carpstrategy.domain.assistant

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AiUsageTest {
    // 25/10/2026 08:30 en España (07:30 UTC) es aún el 25/10 a las 00:30 en California.
    private val now = Instant.parse("2026-10-25T07:30:00Z")

    @Test
    fun `dia de cuota de Google y su renovacion`() {
        assertEquals(LocalDate.of(2026, 10, 25), AiUsage.dayOf(now))
        // Antes de las 07:00 UTC (medianoche en California con horario de verano) cuenta como el día anterior.
        assertEquals(LocalDate.of(2026, 10, 24), AiUsage.dayOf(Instant.parse("2026-10-25T06:59:00Z")))
        assertEquals(Instant.parse("2026-10-26T07:00:00Z"), AiUsage.nextReset(now))
    }

    @Test
    fun `cuenta consultas, tokens, busquedas y errores de cuota, y guarda 7 dias`() {
        var days = emptyList<UsageDay>()
        days = AiUsage.record(days, AiCall(now, AiKind.PLAN, inputTokens = 1000, outputTokens = 200, thinkingTokens = 300, searches = 0))
        days = AiUsage.record(days, AiCall(now.plusSeconds(60), AiKind.BAIT, inputTokens = 100, outputTokens = 50, searches = 2))
        days = AiUsage.record(days, AiCall(now.plusSeconds(120), AiKind.BAIT, quota = true, quotaDetail = "Quota exceeded"))
        val today = AiUsage.today(days, now)
        assertEquals(2, today.requests)
        assertEquals(1650, today.tokens)
        assertEquals(2, today.searches)
        assertEquals(1, today.quotaErrors)
        assertEquals("Quota exceeded", today.lastQuotaDetail)
        assertEquals(mapOf(AiKind.PLAN to 1, AiKind.BAIT to 1), today.byKind)
        // Ocho días después, el primero ya no se guarda.
        val later = AiUsage.record(days, AiCall(now.plusSeconds(8 * 86_400L), AiKind.CHAT))
        assertEquals(1, later.size)
        assertEquals(7, AiUsage.lastDays(days, now).size)
    }

    @Test
    fun `los dos moviles suman y el aviso salta al 80 por ciento`() {
        val a = AiUsage.record(emptyList(), AiCall(now, AiKind.PLAN, inputTokens = 10))
        val b = AiUsage.record(emptyList(), AiCall(now, AiKind.CHECK_IN, inputTokens = 5))
        val merged = AiUsage.today(AiUsage.merge(listOf(a, b)), now)
        assertEquals(2, merged.requests)
        assertEquals(15, merged.inputTokens)
        assertNull(merged.lastQuotaAt)
        assertEquals(LimitLevel.OK, AiUsage.level(79, 100))
        assertEquals(LimitLevel.NEAR, AiUsage.level(80, 100))
        assertEquals(LimitLevel.OVER, AiUsage.level(100, 100))
        assertEquals(LimitLevel.OK, AiUsage.level(500, null))
    }
}
