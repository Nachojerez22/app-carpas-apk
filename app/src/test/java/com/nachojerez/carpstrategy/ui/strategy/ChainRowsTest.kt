package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.domain.rules.ActiveRule
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.LevelResult
import com.nachojerez.carpstrategy.domain.rules.Rule
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import com.nachojerez.carpstrategy.domain.rules.RuleType
import com.nachojerez.carpstrategy.domain.rules.RuleWeight
import com.nachojerez.carpstrategy.domain.rules.StrategyResult
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChainRowsTest {
    private fun rule(id: String, level: RuleLevel, type: RuleType = RuleType.MULTIPLIER) =
        Rule(id, level, type, Evidence.GREEN, RuleWeight.HIGH, "descripción $id")

    private fun active(rule: Rule, factor: Double) = ActiveRule(rule, emptyList(), factor)

    private fun result(levels: List<LevelResult>, limiting: RuleLevel?, blocking: List<ActiveRule> = emptyList()) = StrategyResult(
        evaluatedAt = Instant.parse("2026-10-03T10:00:00Z"),
        blocked = blocking.isNotEmpty(),
        blockingRules = blocking,
        favorability = if (blocking.isEmpty()) 0.5 else null,
        band = null,
        levels = levels,
        limitingLevel = limiting,
        demand = null,
        advice = emptyMap(),
        activeRules = levels.flatMap { it.rules } + blocking,
        notEvaluable = emptyList(),
    )

    @Test
    fun `el nivel mas bajo limita, los demas restan, superan o son neutros`() {
        val temp = active(rule("T1", RuleLevel.TEMPERATURE), 0.5)
        val wind = active(rule("V1", RuleLevel.PHYSICAL), 0.9)
        val wind2 = active(rule("V2", RuleLevel.PHYSICAL), 0.8)
        val ok = active(rule("C1", RuleLevel.CATCHABILITY, RuleType.ADVICE), 1.0)
        val rows = chainRows(
            result(
                listOf(
                    LevelResult(RuleLevel.HABITAT, 1.0, emptyList()),
                    LevelResult(RuleLevel.TEMPERATURE, 0.5, listOf(temp)),
                    LevelResult(RuleLevel.PHYSICAL, 0.72, listOf(wind, wind2)),
                    LevelResult(RuleLevel.CATCHABILITY, 1.0, listOf(ok)),
                ),
                limiting = RuleLevel.TEMPERATURE,
            ),
        )
        assertEquals(
            listOf(LevelStatus.PASSED, LevelStatus.NEUTRAL, LevelStatus.LIMITS, LevelStatus.REDUCES, LevelStatus.PASSED),
            rows.map { it.status },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), rows.map { it.level.order })
        assertEquals("descripción T1", rows[2].why)
        // El porqué es la regla que más resta del nivel.
        assertEquals("descripción V2", rows[3].why)
        assertNull(rows[4].why)
    }

    @Test
    fun `un filtro duro bloquea y deja neutros los demas niveles`() {
        val legal = active(rule("L1", RuleLevel.LEGALITY, RuleType.HARD_FILTER), 0.0)
        val temp = active(rule("T1", RuleLevel.TEMPERATURE), 0.5)
        val rows = chainRows(
            result(listOf(LevelResult(RuleLevel.TEMPERATURE, 0.5, listOf(temp))), limiting = null, blocking = listOf(legal)),
        )
        assertEquals(LevelStatus.BLOCKS, rows[0].status)
        assertEquals("descripción L1", rows[0].why)
        assertEquals(LevelStatus.NEUTRAL, rows[1].status)
    }
}
