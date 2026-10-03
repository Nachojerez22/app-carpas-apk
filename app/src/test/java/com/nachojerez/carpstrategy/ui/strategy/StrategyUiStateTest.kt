package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.rules.RulesJson
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.RuleIssue
import com.nachojerez.carpstrategy.domain.rules.RuleIssueCode
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrategyUiStateTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val point = GeoPoint(38.35, -6.70)
    private val rules = RulesJson.parse(File("src/main/assets/rules.json").readText())

    @Test
    fun `reglas invalidas se muestran como errores`() {
        val invalid = RuleLoadResult.Invalid(listOf(RuleIssue(1, "x", "peso", RuleIssueCode.RED_WITH_WEIGHT)))
        val state = buildStrategyState(RawWeather(null, emptyList(), null), invalid, now, point)
        assertEquals(1, state.ruleIssues.size)
        assertNull(state.result)
        assertFalse(state.isLoading)
    }

    @Test
    fun `sin datos no hay evaluacion pero si fecha de normativa`() {
        val state = buildStrategyState(RawWeather(null, emptyList(), null), rules, now, point)
        assertNull(state.result)
        assertEquals("2026-03-18", state.regulationReviewed)
    }

    @Test
    fun `con una medida del agua se evalua y hay ventanas legales`() {
        val water = ManualRecord(
            period = RecordPeriod.At(now.minus(Duration.ofHours(1))), location = point, source = "termómetro",
            origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.0), createdAt = now,
        )
        val state = buildStrategyState(RawWeather(null, emptyList(), null, manualRecords = listOf(water)), rules, now, point)
        val result = state.result!!
        assertFalse(result.blocked)
        assertTrue(result.favorability!! > 0.0)
        assertTrue(state.windows.isNotEmpty())
        val legal = state.derived!!.legalToday!!
        assertTrue(state.windows.all { it.start in legal && it.end in legal })
    }
}
