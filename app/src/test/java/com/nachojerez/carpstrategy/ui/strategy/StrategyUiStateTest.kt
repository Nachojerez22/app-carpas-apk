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
import com.nachojerez.carpstrategy.ui.conditions.Formatting
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

    @Test
    fun `plan preparado - vale toda la salida aunque cambie la franja, no si cambia lo elegido`() {
        val key = "2026-10-26|brovales|auto|2|DAWN|1|-"
        assertEquals(outingKey(key), outingKey("2026-10-26|brovales|auto|2|MORNING|1|-"))
        assertTrue(outingKey(key) != outingKey("2026-10-26|brovales|auto|3|MORNING|1|-"))
        assertTrue(outingKey(key) != outingKey("2026-10-27|brovales|auto|2|DAWN|1|-"))

        val water = ManualRecord(
            period = RecordPeriod.At(now.minus(Duration.ofHours(1))), location = point, source = "termómetro",
            origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.0), createdAt = now,
        )
        val base = buildStrategyState(RawWeather(null, emptyList(), null, manualRecords = listOf(water)), rules, now, point)
        val gear = listOf(GearItem("1", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT))
        val state = base.copy(gear = gear, advice = adviceFor(base.derived, now, emptyList(), gear, emptyList(), emptyList()))
        val current = planKey(state)!!
        val parts = current.split("|").toMutableList()
        parts[4] = if (parts[4] == "DAWN") "EVENING" else "DAWN"
        val preparedKey = parts.joinToString("|")
        val ai = AiPlan("Preparado", Evidence.YELLOW, listOf(AiRodPlan(1, null, "Chufa", reason = "Fondo duro", evidence = Evidence.YELLOW)))
        val prepared = AiOutcome(AiExchange(now, AiKind.PLAN, "GEMINI", "modelo", valid = true), ai)
        assertEquals(prepared, aiPlanFor(state, PlanUiState(prepared = prepared, preparedKey = preparedKey)))
        assertEquals(PlanSource.AI, dayPlanFor(state, PlanUiState(prepared = prepared, preparedKey = preparedKey))!!.source)
        // Preparado con 3 cañas y ahora llevas otras: no vale.
        parts[3] = "3"
        assertEquals(null, aiPlanFor(state, PlanUiState(prepared = prepared, preparedKey = parts.joinToString("|"))))
    }

    @Test
    fun `proxima salida - hoy si aun no ha empezado el horario legal, si no manana`() {
        val zone = Formatting.MADRID
        // 25/10/2026 en Brovales: orto ~08:10 (hora de invierno) ⇒ inicio legal ~07:10.
        val night = Instant.parse("2026-10-25T03:00:00Z")
        val evening = Instant.parse("2026-10-25T19:00:00Z")
        val forNight = nextOuting(night, point, zone)!!
        val forEvening = nextOuting(evening, point, zone)!!
        assertEquals(java.time.LocalDate.of(2026, 10, 25), forNight.atZone(zone).toLocalDate())
        assertEquals(java.time.LocalDate.of(2026, 10, 26), forEvening.atZone(zone).toLocalDate())
        // Media hora después del inicio legal (1 h antes del orto).
        val sun = com.nachojerez.carpstrategy.domain.derived.SolarCalculator.sunTimes(java.time.LocalDate.of(2026, 10, 26), point)
        assertEquals(sun.sunrise!!.minus(Duration.ofHours(1)).plus(PREP_AFTER_LEGAL_START), forEvening)
    }

    @Test
    fun `aviso de la proxima salida - solo favorable, legal y una vez por salida`() {
        val day = java.time.LocalDate.of(2026, 10, 26)
        assertTrue(shouldNotifyOutlook(com.nachojerez.carpstrategy.domain.rules.FavorabilityBand.FAVORABLE, false, day, null))
        assertTrue(shouldNotifyOutlook(com.nachojerez.carpstrategy.domain.rules.FavorabilityBand.VERY_FAVORABLE, false, day, day.minusDays(1)))
        assertFalse(shouldNotifyOutlook(com.nachojerez.carpstrategy.domain.rules.FavorabilityBand.FAVORABLE, false, day, day))
        assertFalse(shouldNotifyOutlook(com.nachojerez.carpstrategy.domain.rules.FavorabilityBand.INTERMEDIATE, false, day, null))
        assertFalse(shouldNotifyOutlook(com.nachojerez.carpstrategy.domain.rules.FavorabilityBand.FAVORABLE, true, day, null))
        assertFalse(shouldNotifyOutlook(null, false, day, null))
    }

    @Test
    fun `el plan preparado solo pasa a la guiada si es de ese dia y ese embalse`() {
        val day = java.time.LocalDate.of(2026, 10, 26)
        val key = "2026-10-26|brovales|auto|2|DAWN|1|-"
        assertTrue(preparedPlanFits(key, "brovales", day))
        assertFalse(preparedPlanFits(key, "valuengo", day))
        assertFalse(preparedPlanFits(key, "brovales", day.plusDays(1)))
        assertTrue(preparedPlanFits("2026-10-26|$NO_RESERVOIR|auto|2|DAWN|1|-", null, day))
        assertFalse(preparedPlanFits(null, "brovales", day))
    }
}
