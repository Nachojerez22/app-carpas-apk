package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.data.rules.RulesJson
import com.nachojerez.carpstrategy.domain.advice.PlanSource
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiRodPlan
import com.nachojerez.carpstrategy.domain.assistant.AiSpotPick
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.RuleIssue
import com.nachojerez.carpstrategy.domain.rules.RuleIssueCode
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.TimeWindow
import com.nachojerez.carpstrategy.domain.rules.WindowKind
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

    @Test
    fun `proxima ventana sugerida en curso o siguiente`() {
        val base = Instant.parse("2026-10-04T06:00:00Z")
        val dawn = TimeWindow(base, base.plus(Duration.ofHours(2)), WindowKind.DAWN, Evidence.YELLOW)
        val dusk = TimeWindow(base.plus(Duration.ofHours(10)), base.plus(Duration.ofHours(12)), WindowKind.DUSK, Evidence.YELLOW)
        val windows = listOf(dusk, dawn)
        assertEquals(dawn, nextWindow(windows, base.plus(Duration.ofHours(1))))
        assertEquals(dusk, nextWindow(windows, base.plus(Duration.ofHours(3))))
        assertNull(nextWindow(windows, base.plus(Duration.ofHours(13))))
        assertNull(nextWindow(emptyList(), base))
    }

    @Test
    fun `plan del dia - la IA solo cuenta si se pidio para lo elegido y en automatico manda su puesto`() {
        val water = ManualRecord(
            period = RecordPeriod.At(now.minus(Duration.ofHours(1))), location = point, source = "termómetro",
            origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.0), createdAt = now,
        )
        val base = buildStrategyState(RawWeather(null, emptyList(), null, manualRecords = listOf(water)), rules, now, point)
        val spots = listOf(Spot(1, "Punta", structure = SpotStructure.SHALLOW_EDGE), Spot(2, "Caída", structure = SpotStructure.FIRST_DROP))
        val gear = listOf(GearItem("1", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT))
        val state = base.copy(spots = spots, gear = gear, advice = adviceFor(base.derived, now, emptyList(), gear, spots, emptyList()))
        val key = planKey(state)!!
        // Otro puesto u otras cañas: otro plan.
        assertTrue(key != planKey(state.copy(chosenSpotId = 2)))
        assertTrue(key != planKey(state.copy(rods = 3)))

        val ai = AiPlan(
            "Plan", Evidence.YELLOW, listOf(AiRodPlan(1, "Caída", "Chufa", reason = "Fondo duro", evidence = Evidence.YELLOW)),
            bestSpot = AiSpotPick("Caída", "Más honda", Evidence.YELLOW),
        )
        val outcome = AiOutcome(AiExchange(now, AiKind.PLAN, "GEMINI", "modelo", valid = true), ai)
        val withAi = dayPlanFor(state, PlanUiState(outcome = outcome, key = key))!!
        assertEquals("Caída", withAi.spot?.spot?.name)
        assertEquals(PlanSource.AI, withAi.source)
        // Pedido para otra elección: no vale y manda la app.
        val stale = dayPlanFor(state, PlanUiState(outcome = outcome, key = "otra"))!!
        assertEquals(PlanSource.APP, stale.source)
        assertEquals(DEFAULT_RODS, stale.rods.size)
    }
}
