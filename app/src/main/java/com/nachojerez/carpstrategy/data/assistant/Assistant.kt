package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiContext
import com.nachojerez.carpstrategy.domain.assistant.AiDecision
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiValidator
import java.time.Instant

/** Resultado de una consulta: el registro (siempre) y la respuesta solo si es válida. */
data class AiOutcome<T>(val exchange: AiExchange, val value: T?)

/**
 * Consulta a la IA y valida la respuesta. Si falla la red o la respuesta no cumple las normas,
 * [AiOutcome.value] es null y la app sigue con sus reglas. Bloqueante: llamar desde IO.
 */
class Assistant(private val client: AiClient) {

    fun checkIn(config: AiConfig, apiKey: String, state: String, ctx: AiContext): AiOutcome<AiDecision> =
        run(config, apiKey, AiKind.CHECK_IN, AssistantPrompts.CHECK_IN_SYSTEM, state, ctx.now, AssistantJson::parseDecision, { AiValidator.validate(it, ctx) }, { it.summary }, { it.reasons.firstOrNull() })

    fun plan(config: AiConfig, apiKey: String, state: String, ctx: AiContext): AiOutcome<AiPlan> =
        run(config, apiKey, AiKind.PLAN, AssistantPrompts.PLAN_SYSTEM, state, ctx.now, AssistantJson::parsePlan, { AiValidator.validate(it, ctx) }, { "PLAN ${it.rods.size}" }, { it.summary })

    /** Prueba de conexión: pide un JSON mínimo. Devuelve el error o null si todo va bien. */
    fun test(config: AiConfig, apiKey: String): AiException? = try {
        val text = client.complete(config, apiKey, TEST_SYSTEM, TEST_USER)
        if (AssistantJson.objectIn(text) == null) AiException.Empty() else null
    } catch (e: AiException) {
        e
    }

    private fun <T> run(
        config: AiConfig,
        apiKey: String,
        kind: AiKind,
        system: String,
        user: String,
        now: Instant,
        parse: (String) -> T?,
        validate: (T) -> Set<AiIssue>,
        summary: (T) -> String,
        note: (T) -> String?,
    ): AiOutcome<T> {
        val base = AiExchange(time = now, kind = kind, provider = config.provider.name, model = config.model)
        val text = try {
            client.complete(config, apiKey, system, user)
        } catch (e: AiException) {
            return AiOutcome(base.copy(error = e::class.simpleName), null)
        }
        val stored = text.take(AssistantJson.MAX_STORED)
        val parsed = parse(text) ?: return AiOutcome(base.copy(response = stored, issues = listOf(AiIssue.UNREADABLE)), null)
        val issues = validate(parsed)
        val valid = issues.isEmpty()
        val exchange = base.copy(
            response = stored,
            valid = valid,
            issues = issues.sortedBy { it.ordinal },
            summary = summary(parsed),
            note = if (valid) note(parsed)?.take(AiValidator.MAX_TEXT) else null,
        )
        return AiOutcome(exchange, parsed.takeIf { issues.isEmpty() })
    }

    private companion object {
        const val TEST_SYSTEM = "Responde solo con JSON."
        const val TEST_USER = """Devuelve exactamente {"ok":true}"""
    }
}
