package com.nachojerez.carpstrategy.data.rules

import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.RuleIssueCode
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.RuleType
import com.nachojerez.carpstrategy.domain.rules.RuleWeight
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class RulesJsonTest {
    @Test
    fun `la huella identifica el contenido de las reglas`() {
        val text = File("src/main/assets/rules.json").readText()
        val loaded = RulesJson.parse(text) as RuleLoadResult.Loaded
        assertEquals(12, loaded.ruleSet.fingerprint!!.length)
        assertEquals(RulesJson.fingerprint(text), loaded.ruleSet.fingerprint)
        assertTrue(RulesJson.fingerprint(text + " ") != loaded.ruleSet.fingerprint)
    }

    private fun issues(text: String) = (RulesJson.parse(text) as? RuleLoadResult.Invalid)?.issues
        ?: fail("Se esperaban errores")

    private fun file(rules: String) = """{"formato":"carpstrategy-reglas","version":1,"reglas":[$rules]}"""

    private val okRule = """{"id":"r","nivel":2,"tipo":"consejo","evidencia":"amarillo","peso":"bajo","descripcion":"d","condiciones":{}}"""

    @Test
    fun `el rules json de la app es valido y cumple las normas del documento`() {
        val result = RulesJson.parse(File("src/main/assets/rules.json").readText())
        val loaded = result as? RuleLoadResult.Loaded ?: fail("rules.json inválido: $result")
        val rules = loaded.ruleSet.rules
        assertTrue(rules.size >= 25)
        assertEquals("2026-03-18", loaded.ruleSet.regulationReviewed)
        // §0.2: nada sin evidencia pesa. §0.6: el horario legal es filtro duro.
        assertTrue(rules.filter { it.evidence == Evidence.RED }.all { it.weight == RuleWeight.ZERO })
        assertTrue(rules.filter { it.level == RuleLevel.EXPLORATORY }.map { it.id }.containsAll(listOf("luna", "presion_barometrica", "nubosidad")))
        assertEquals(RuleType.HARD_FILTER, rules.first { it.id == "horario_legal" }.type)
        assertEquals(RuleType.MEMBERSHIP, rules.first { it.id == "temp_agua_demanda" }.type)
    }

    @Test
    fun `errores de archivo`() {
        assertEquals(RuleIssueCode.NOT_JSON, issues("nada").single().code)
        assertEquals(RuleIssueCode.BAD_FORMAT, issues("""{"formato":"x"}""").single().code)
        assertEquals(RuleIssueCode.UNSUPPORTED_VERSION, issues("""{"formato":"carpstrategy-reglas","version":9}""").single().code)
        assertEquals(RuleIssueCode.NO_RULES, issues(file("")).single().code)
    }

    @Test
    fun `campos obligatorios, valores y ids`() {
        val found = issues(
            file(
                """
                $okRule,
                {"id":"r","nivel":7,"tipo":"magia","evidencia":"verde","peso":"alto","descripcion":"d"},
                {"id":"Mal Id","nivel":1,"tipo":"consejo","evidencia":"verde","peso":"alto"},
                "texto"
                """,
            ),
        )
        assertEquals(
            listOf(
                2 to RuleIssueCode.DUPLICATE_ID,
                2 to RuleIssueCode.INVALID_VALUE,
                2 to RuleIssueCode.INVALID_VALUE,
                3 to RuleIssueCode.INVALID_ID,
                3 to RuleIssueCode.MISSING_FIELD,
                4 to RuleIssueCode.RULE_NOT_OBJECT,
            ),
            found.map { it.ruleNumber to it.code },
        )
    }

    @Test
    fun `condiciones invalidas`() {
        val found = issues(
            file(
                """
                {"id":"a","nivel":2,"tipo":"consejo","evidencia":"amarillo","peso":"bajo","descripcion":"d",
                 "condiciones":{"temperatura":{"min":1},"temp_agua_c":{"min":20,"max":10},"escorrentia":{"min":1},
                                "estacion":{"en":["monzon"]},"lluvia_24h_mm":{}}}
                """,
            ),
        )
        assertEquals(
            setOf(
                RuleIssueCode.UNKNOWN_PARAMETER,
                RuleIssueCode.MIN_GREATER_THAN_MAX,
                RuleIssueCode.WRONG_PARAMETER_TYPE,
                RuleIssueCode.INVALID_VALUE,
                RuleIssueCode.EMPTY_CONDITION,
            ),
            found.map { it.code }.toSet(),
        )
        assertTrue(found.any { it.path == "condiciones.temperatura" })
    }

    @Test
    fun `coherencia con el documento de conocimiento`() {
        val found = issues(
            file(
                """
                {"id":"a","nivel":"exploratoria","tipo":"registro","evidencia":"rojo","peso":"medio","descripcion":"d"},
                {"id":"b","nivel":2,"tipo":"filtro_duro","evidencia":"verde","peso":"alto","descripcion":"d"},
                {"id":"c","nivel":2,"tipo":"multiplicador","evidencia":"verde","peso":"alto","descripcion":"d","factor":1.5},
                {"id":"d","nivel":2,"tipo":"multiplicador","evidencia":"verde","peso":"alto","descripcion":"d"},
                {"id":"e","nivel":2,"tipo":"pertenencia","evidencia":"verde","peso":"alto","descripcion":"d",
                 "pertenencia":{"parametro":"temp_agua_c","puntos":[[10,0.5],[5,0.8]]}},
                {"id":"f","nivel":2,"tipo":"consejo","evidencia":"verde","peso":"alto","descripcion":"d",
                 "estrategia":[{"campo":"donde","texto":"hacia {marea}"},{"campo":"lugar","texto":"x"}]}
                """,
            ),
        )
        val byRule = found.groupBy({ it.ruleId }, { it.code })
        assertEquals(listOf(RuleIssueCode.RED_WITH_WEIGHT, RuleIssueCode.EXPLORATORY_WITH_WEIGHT), byRule["a"])
        assertEquals(listOf(RuleIssueCode.HARD_FILTER_NOT_LEVEL_0), byRule["b"])
        assertEquals(listOf(RuleIssueCode.FACTOR_OUT_OF_RANGE), byRule["c"])
        assertEquals(listOf(RuleIssueCode.MISSING_FIELD), byRule["d"])
        assertEquals(listOf(RuleIssueCode.INVALID_MEMBERSHIP), byRule["e"])
        assertEquals(listOf(RuleIssueCode.UNKNOWN_PLACEHOLDER, RuleIssueCode.INVALID_VALUE), byRule["f"])
    }

    @Test
    fun `todo o nada`() {
        val result = RulesJson.parse(file("$okRule, {\"id\":\"x\"}"))
        assertTrue(result is RuleLoadResult.Invalid)
    }
}
