package com.nachojerez.carpstrategy.domain.rules

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleEngineTest {
    private val at = Instant.parse("2026-10-03T10:00:00Z")

    private fun rule(
        id: String,
        level: RuleLevel,
        type: RuleType = RuleType.ADVICE,
        weight: RuleWeight = RuleWeight.HIGH,
        evidence: Evidence = Evidence.YELLOW,
        conditions: List<Condition> = emptyList(),
        factor: Double? = null,
        membership: Membership? = null,
        strategy: List<StrategyItem> = emptyList(),
    ) = Rule(id, level, type, evidence, weight, "desc $id", null, conditions, factor, membership, strategy)

    private val demand = rule(
        "demanda", RuleLevel.TEMPERATURE, RuleType.MEMBERSHIP,
        membership = Membership(RuleParameter.WATER_TEMP, listOf(10.0 to 0.2, 20.0 to 0.6, 26.0 to 1.0)),
    )

    private fun ctx(water: Double? = 20.0, numbers: Map<RuleParameter, Double> = emptyMap(), booleans: Map<RuleParameter, Boolean> = emptyMap()) =
        RuleContext(numbers + listOfNotNull(water?.let { RuleParameter.WATER_TEMP to it }), booleans)

    @Test
    fun `curva de pertenencia lineal a trozos`() {
        val m = demand.membership!!
        assertEquals(0.2, m.valueAt(5.0), 1e-9)
        assertEquals(0.4, m.valueAt(15.0), 1e-9)
        assertEquals(1.0, m.valueAt(30.0), 1e-9)
    }

    @Test
    fun `factor efectivo segun el peso`() {
        assertEquals(0.8, RuleEngine.effective(0.8, RuleWeight.HIGH), 1e-9)
        assertEquals(0.88, RuleEngine.effective(0.8, RuleWeight.MEDIUM), 1e-9)
        assertEquals(0.94, RuleEngine.effective(0.8, RuleWeight.LOW), 1e-9)
        assertEquals(1.0, RuleEngine.effective(0.0, RuleWeight.ZERO), 1e-9)
    }

    @Test
    fun `cadena de niveles - el producto no deja que un nivel alto compense uno bajo`() {
        val habitat = rule("nivel_bajando", RuleLevel.HABITAT, RuleType.MULTIPLIER, factor = 0.5,
            conditions = listOf(Condition.Range(RuleParameter.LEVEL_DELTA_7D, null, -0.8)))
        val result = RuleEngine.evaluate(
            RuleSet(listOf(demand, habitat), null, null),
            ctx(26.0, mapOf(RuleParameter.LEVEL_DELTA_7D to -1.0)),
            at,
        )
        assertEquals(0.5, result.favorability!!, 1e-9, "Agua perfecta (1,0) × hábitat 0,5")
        assertEquals(RuleLevel.HABITAT, result.limitingLevel)
        assertEquals(FavorabilityBand.INTERMEDIATE, result.band)
        assertFalse(result.blocked)
    }

    @Test
    fun `el filtro duro bloquea la recomendacion`() {
        val legal = rule("horario_legal", RuleLevel.LEGALITY, RuleType.HARD_FILTER, evidence = Evidence.REGULATION,
            conditions = listOf(Condition.Is(RuleParameter.LEGAL_WINDOW_AVAILABLE, false)))
        val blocked = RuleEngine.evaluate(RuleSet(listOf(legal, demand), null, null), ctx(booleans = mapOf(RuleParameter.LEGAL_WINDOW_AVAILABLE to false)), at)
        assertTrue(blocked.blocked)
        assertEquals(0.0, blocked.favorability!!, 1e-9)
        assertEquals("horario_legal", blocked.blockingRules.single().rule.id)

        val open = RuleEngine.evaluate(RuleSet(listOf(legal, demand), null, null), ctx(booleans = mapOf(RuleParameter.LEGAL_WINDOW_AVAILABLE to true)), at)
        assertFalse(open.blocked)
    }

    @Test
    fun `sin temperatura del agua no hay valoracion y se listan las reglas sin datos`() {
        val result = RuleEngine.evaluate(RuleSet(listOf(demand), null, null), ctx(water = null), at)
        assertNull(result.favorability)
        assertNull(result.demand)
        assertEquals(listOf(RuleParameter.WATER_TEMP), result.notEvaluable.single().second)
    }

    @Test
    fun `las reglas de peso cero aparecen pero no modifican`() {
        val moon = rule("luna", RuleLevel.EXPLORATORY, RuleType.RECORD, RuleWeight.ZERO, Evidence.RED)
        val warning = rule("aviso", RuleLevel.TEMPERATURE, RuleType.WARNING, RuleWeight.ZERO)
        val result = RuleEngine.evaluate(RuleSet(listOf(demand, moon, warning), null, null), ctx(26.0), at)
        assertEquals(1.0, result.favorability!!, 1e-9)
        assertTrue(result.activeRules.map { it.rule.id }.containsAll(listOf("luna", "aviso")))
        assertNull(result.limitingLevel, "Ningún nivel limita")
    }

    @Test
    fun `condiciones de rango, booleanas y de texto con el porque`() {
        val spawning = rule(
            "desove", RuleLevel.TEMPERATURE, RuleType.MULTIPLIER, factor = 0.85,
            conditions = listOf(Condition.Range(RuleParameter.WATER_TEMP, 17.0, 21.0), Condition.OneOf(RuleParameter.SEASON, setOf("primavera"))),
        )
        val spring = RuleEngine.evaluate(RuleSet(listOf(spawning), null, null), RuleContext(mapOf(RuleParameter.WATER_TEMP to 18.0), texts = mapOf(RuleParameter.SEASON to "primavera")), at)
        val active = spring.activeRules.single()
        assertEquals(listOf(18.0, "primavera"), active.checks.map { it.actual })
        assertEquals(0.85, active.appliedFactor, 1e-9) // peso alto: factor completo

        val autumn = RuleEngine.evaluate(RuleSet(listOf(spawning), null, null), RuleContext(mapOf(RuleParameter.WATER_TEMP to 18.0), texts = mapOf(RuleParameter.SEASON to "otono")), at)
        assertTrue(autumn.activeRules.isEmpty())
    }

    @Test
    fun `estrategia agrupada por campo, sin duplicados y con marcadores sustituidos`() {
        val wind = rule("viento", RuleLevel.PHYSICAL, strategy = listOf(StrategyItem(StrategyField.WHERE, "Sopla del {viento_desde} hacia el {viento_hacia}", null)))
        val cold = rule("frio", RuleLevel.TEMPERATURE, evidence = Evidence.GREEN, strategy = listOf(
            StrategyItem(StrategyField.BAIT, "Reducir cebado", null),
            StrategyItem(StrategyField.WHERE, "Zonas estables", Evidence.PURPLE),
        ))
        val dup = rule("dup", RuleLevel.PHYSICAL, strategy = listOf(StrategyItem(StrategyField.BAIT, "Reducir cebado", null)))
        val result = RuleEngine.evaluate(
            RuleSet(listOf(wind, cold, dup), null, null),
            RuleContext(placeholders = mapOf("viento_desde" to "SO", "viento_hacia" to "NE")),
            at,
        )
        val where = result.advice.getValue(StrategyField.WHERE)
        assertEquals(listOf("Zonas estables", "Sopla del SO hacia el NE"), where.map { it.text }, "Ordenado por nivel")
        assertEquals(Evidence.PURPLE, where.first().evidence)
        assertEquals(1, result.advice.getValue(StrategyField.BAIT).size)
    }

    @Test
    fun `bandas de demanda alimentaria por temperatura del agua`() {
        assertEquals(FeedingDemand.VERY_LOW, RuleEngine.demand(8.0))
        assertEquals(FeedingDemand.LOW, RuleEngine.demand(12.0))
        assertEquals(FeedingDemand.MEDIUM, RuleEngine.demand(18.0))
        assertEquals(FeedingDemand.HIGH, RuleEngine.demand(22.0))
        assertEquals(FeedingDemand.VERY_HIGH, RuleEngine.demand(26.5))
        assertEquals(FeedingDemand.UNCERTAIN_HEAT, RuleEngine.demand(29.0))
    }
}
