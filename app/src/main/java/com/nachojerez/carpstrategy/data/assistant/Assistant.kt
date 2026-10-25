package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.assistant.AiBaitDraft
import com.nachojerez.carpstrategy.domain.assistant.AiChatReply
import com.nachojerez.carpstrategy.domain.assistant.AiContext
import com.nachojerez.carpstrategy.domain.assistant.AiDecision
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiSheetDraft
import com.nachojerez.carpstrategy.domain.assistant.AiValidator
import com.nachojerez.carpstrategy.domain.guided.BaitSheet
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant

/** Resultado de una consulta: el registro (siempre) y la respuesta solo si es válida. */
data class AiOutcome<T>(val exchange: AiExchange, val value: T?)

/**
 * Consulta a la IA y valida la respuesta. Si falla la red o la respuesta no cumple las normas,
 * [AiOutcome.value] es null y la app sigue con sus reglas. Bloqueante: llamar desde IO.
 */
class Assistant(private val client: AiClient) {

    fun checkIn(config: AiConfig, apiKey: String, state: String, ctx: AiContext, knowledge: String? = null): AiOutcome<AiDecision> =
        run(config, apiKey, AiKind.CHECK_IN, AssistantPrompts.withKnowledge(AssistantPrompts.CHECK_IN_SYSTEM, knowledge), state, ctx.now, AssistantJson::parseDecision, { AiValidator.validate(it, ctx) }, { it.summary }, { it.reasons.firstOrNull() })

    fun plan(config: AiConfig, apiKey: String, state: String, ctx: AiContext, knowledge: String? = null): AiOutcome<AiPlan> =
        run(config, apiKey, AiKind.PLAN, AssistantPrompts.withKnowledge(AssistantPrompts.PLAN_SYSTEM, knowledge), state, ctx.now, AssistantJson::parsePlan, { AiValidator.validate(it, ctx) }, { "PLAN ${it.rods.size}" }, { it.summary })

    /**
     * Repaso de una sesión terminada: lecciones propuestas. Cada lección se valida por separado;
     * las que no cumplen se descartan (quedan sus motivos en el registro).
     */
    fun review(
        config: AiConfig,
        apiKey: String,
        state: String,
        spots: List<Spot>,
        now: Instant,
        sessionId: Long,
        knowledge: String? = null,
        newId: () -> String,
    ): AiOutcome<List<Lesson>> {
        val rejected = mutableSetOf<AiIssue>()
        val outcome = run(
            config, apiKey, AiKind.REVIEW, AssistantPrompts.withKnowledge(AssistantPrompts.REVIEW_SYSTEM, knowledge), state, now,
            parse = { text ->
                AssistantJson.parseLessons(text)?.mapNotNull { draft ->
                    val issues = AiValidator.validate(draft, spots)
                    rejected += issues
                    if (issues.isEmpty()) AiValidator.toLesson(draft, newId(), now, listOf(sessionId)) else null
                }?.take(MAX_LESSONS)
            },
            validate = { emptySet() },
            summary = { "LECCIONES ${it.size}" },
            note = { it.firstOrNull()?.text },
        )
        val exchange = outcome.exchange.let { e -> if (rejected.isEmpty()) e else e.copy(issues = (e.issues + rejected).distinct().sortedBy { it.ordinal }) }
        return AiOutcome(exchange, outcome.value)
    }

    /** Mensaje del chat de la sesión: respuesta validada (texto y, si los hay, cambios por caña). */
    fun chat(config: AiConfig, apiKey: String, state: String, ctx: AiContext, knowledge: String? = null): AiOutcome<AiChatReply> =
        run(
            config, apiKey, AiKind.CHAT, AssistantPrompts.withKnowledge(AssistantPrompts.CHAT_SYSTEM, knowledge), state, ctx.now,
            parse = AssistantJson::parseChat,
            validate = { reply: AiChatReply -> AiValidator.validate(reply, ctx) },
            summary = { if (it.changes.isEmpty()) "RESPUESTA" else "RESPUESTA " + it.changes.joinToString(" ") { c -> "${c.rod}:${c.kind?.name ?: "?"}" } },
            note = { it.text },
        )

    /**
     * Ficha de un embalse preparada por la IA con el documento (§5). Si no es válida, no se
     * guarda nada y la ficha anterior sigue igual.
     */
    fun sheet(config: AiConfig, apiKey: String, reservoir: Reservoir, previous: ReservoirSheet?, now: Instant, knowledge: String? = null): AiOutcome<ReservoirSheet> {
        val outcome = run(
            config, apiKey, AiKind.SHEET, AssistantPrompts.withKnowledge(AssistantPrompts.SHEET_SYSTEM, knowledge), AssistantPrompts.sheetState(reservoir, previous), now,
            parse = AssistantJson::parseSheet,
            validate = { draft: AiSheetDraft -> AiValidator.validate(draft) },
            summary = { "FICHA ${it.species.count { s -> s.value }}" },
            note = { it.summary },
        )
        return AiOutcome(outcome.exchange, outcome.value?.let { AiValidator.toSheet(it, reservoir.id, previous, now) })
    }

    /**
     * Ficha de un cebo (fase 7.13): marca, nombre y enlace; con Gemini busca en Google y lee el
     * enlace. Si no es válida, el cebo se queda como estaba.
     */
    fun baitSheet(config: AiConfig, apiKey: String, item: GearItem, now: Instant): AiOutcome<BaitSheet> {
        fun ask(web: Boolean) = run(
            config, apiKey, AiKind.BAIT, if (web) AssistantPrompts.BAIT_SYSTEM else AssistantPrompts.BAIT_SYSTEM_NO_SEARCH, AssistantPrompts.baitState(item), now,
            parse = AssistantJson::parseBait,
            validate = { draft: AiBaitDraft -> AiValidator.validate(draft) },
            summary = { "CEBO ${it.type?.name ?: "?"}" },
            note = { it.composition },
            web = web,
        )
        val searched = ask(web = config.provider == AiProvider.GEMINI)
        searched.value?.let { return AiOutcome(searched.exchange, AiValidator.toSheet(it, now).copy(searched = config.provider == AiProvider.GEMINI)) }
        // La búsqueda en Google tiene su propia cuota (en el nivel gratuito, no en todos los
        // modelos): si Google la rechaza, se pide la ficha solo con lo que sabe la IA.
        if (config.provider != AiProvider.GEMINI || searched.exchange.error !in SEARCH_REFUSED) return AiOutcome(searched.exchange, null)
        val plain = ask(web = false)
        val sheet = plain.value?.let { draft ->
            // Sin búsqueda no hay fuentes que valgan: fuera los enlaces y, como mucho, 🟣.
            val evidence = if (draft.evidence == Evidence.GREEN || draft.evidence == Evidence.YELLOW) Evidence.PURPLE else draft.evidence
            AiValidator.toSheet(draft.copy(sources = emptyList(), evidence = evidence), now).copy(searched = false)
        }
        return AiOutcome(plain.exchange, sheet)
    }

    /** Prueba de conexión: pide un JSON mínimo. Devuelve el error o null si todo va bien. */
    fun test(config: AiConfig, apiKey: String): AiException? = try {
        val text = client.complete(config, apiKey, TEST_SYSTEM, TEST_USER)
        if (AssistantJson.objectIn(text) == null) AiException.Empty() else null
    } catch (e: AiException) {
        e
    }

    /** Modelos disponibles con tu clave (o el error). */
    fun models(config: AiConfig, apiKey: String): Result<List<String>> = try {
        Result.success(client.listModels(config, apiKey))
    } catch (e: AiException) {
        Result.failure(e)
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
        web: Boolean = false,
    ): AiOutcome<T> {
        val base = AiExchange(time = now, kind = kind, provider = config.provider.name, model = config.model)
        val text = try {
            client.complete(config, apiKey, system, user, web)
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
        /** Errores con los que Google rechaza la búsqueda (cuota o herramienta no admitida). */
        val SEARCH_REFUSED = setOf(AiException.Quota::class.simpleName, AiException.Failed::class.simpleName)

        /** Lecciones por repaso como mucho. */
        const val MAX_LESSONS = 3
        const val TEST_SYSTEM = "Responde solo con JSON."
        const val TEST_USER = """Devuelve exactamente {"ok":true}"""
    }
}
