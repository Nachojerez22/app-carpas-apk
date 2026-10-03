package com.nachojerez.carpstrategy.domain.derived

import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreshnessPolicyTest {
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    private fun levelForAge(age: Duration) = FreshnessPolicy.evaluate(now.minus(age), now).level

    @Test
    fun `niveles segun la antiguedad`() {
        assertEquals(FreshnessLevel.FRESH, levelForAge(Duration.ZERO))
        assertEquals(FreshnessLevel.FRESH, levelForAge(Duration.ofHours(3)))
        assertEquals(FreshnessLevel.AGING, levelForAge(Duration.ofHours(3).plusMinutes(1)))
        assertEquals(FreshnessLevel.AGING, levelForAge(Duration.ofHours(24)))
        assertEquals(FreshnessLevel.STALE, levelForAge(Duration.ofHours(25)))
    }

    @Test
    fun `una fecha futura (reloj desajustado) cuenta como edad cero`() {
        val freshness = FreshnessPolicy.evaluate(now.plusSeconds(600), now)
        assertEquals(Duration.ZERO, freshness.age)
        assertEquals(FreshnessLevel.FRESH, freshness.level)
    }
}
