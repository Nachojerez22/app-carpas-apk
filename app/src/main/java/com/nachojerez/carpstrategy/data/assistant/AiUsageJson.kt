package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiLimits
import com.nachojerez.carpstrategy.domain.assistant.UsageDay
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Consumo de la IA por día (ajuste `ai_usage_<móvil>`) y tus límites (`ai_limits`), en JSON. */
object AiUsageJson {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DayDto(
        @SerialName("dia") val day: String,
        @SerialName("consultas") val requests: Int = 0,
        @SerialName("tokens_entrada") val inputTokens: Long = 0,
        @SerialName("tokens_salida") val outputTokens: Long = 0,
        @SerialName("tokens_razonamiento") val thinkingTokens: Long = 0,
        @SerialName("busquedas") val searches: Int = 0,
        @SerialName("errores_cuota") val quotaErrors: Int = 0,
        @SerialName("por_tipo") val byKind: Map<String, Int> = emptyMap(),
        @SerialName("ultimo_error_cuota") val lastQuotaAt: String? = null,
        @SerialName("ultimo_error_detalle") val lastQuotaDetail: String? = null,
    )

    @Serializable
    private data class LimitsDto(
        @SerialName("consultas_dia") val requestsPerDay: Int? = null,
        @SerialName("busquedas_dia") val searchesPerDay: Int? = null,
        @SerialName("tokens_dia") val tokensPerDay: Long? = null,
    )

    fun encodeDays(days: List<UsageDay>): String = json.encodeToString(
        ListSerializer(DayDto.serializer()),
        days.map { d ->
            DayDto(
                d.day.toString(), d.requests, d.inputTokens, d.outputTokens, d.thinkingTokens, d.searches, d.quotaErrors,
                d.byKind.mapKeys { it.key.name }, d.lastQuotaAt?.toString(), d.lastQuotaDetail,
            )
        },
    )

    fun decodeDays(text: String?): List<UsageDay> = text?.let { runCatching { json.decodeFromString(ListSerializer(DayDto.serializer()), it) }.getOrNull() }
        .orEmpty().mapNotNull { d ->
            UsageDay(
                day = runCatching { LocalDate.parse(d.day) }.getOrNull() ?: return@mapNotNull null,
                requests = d.requests,
                inputTokens = d.inputTokens,
                outputTokens = d.outputTokens,
                thinkingTokens = d.thinkingTokens,
                searches = d.searches,
                quotaErrors = d.quotaErrors,
                byKind = d.byKind.mapNotNull { (k, v) -> AiKind.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap(),
                lastQuotaAt = d.lastQuotaAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                lastQuotaDetail = d.lastQuotaDetail,
            )
        }

    fun encodeLimits(limits: AiLimits): String = json.encodeToString(LimitsDto.serializer(), LimitsDto(limits.requestsPerDay, limits.searchesPerDay, limits.tokensPerDay))

    fun decodeLimits(text: String?): AiLimits = text?.let { runCatching { json.decodeFromString(LimitsDto.serializer(), it) }.getOrNull() }
        ?.let { AiLimits(it.requestsPerDay, it.searchesPerDay, it.tokensPerDay) } ?: AiLimits()
}
