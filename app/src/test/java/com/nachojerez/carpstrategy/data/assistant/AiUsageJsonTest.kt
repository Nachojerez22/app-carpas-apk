package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiLimits
import com.nachojerez.carpstrategy.domain.assistant.UsageDay
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiUsageJsonTest {
    @Test
    fun `dias y limites ida y vuelta, y texto roto sin romper`() {
        val days = listOf(
            UsageDay(
                LocalDate.of(2026, 10, 25), requests = 3, inputTokens = 100, outputTokens = 20, thinkingTokens = 5, searches = 1, quotaErrors = 1,
                byKind = mapOf(AiKind.PLAN to 2, AiKind.BAIT to 1), lastQuotaAt = Instant.parse("2026-10-25T10:00:00Z"), lastQuotaDetail = "Quota",
            ),
        )
        assertEquals(days, AiUsageJson.decodeDays(AiUsageJson.encodeDays(days)))
        val limits = AiLimits(requestsPerDay = 250, searchesPerDay = 500)
        assertEquals(limits, AiUsageJson.decodeLimits(AiUsageJson.encodeLimits(limits)))
        assertTrue(AiUsageJson.decodeDays("{roto").isEmpty())
        assertEquals(AiLimits(), AiUsageJson.decodeLimits(null))
    }
}
