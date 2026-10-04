package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ajustes del asistente en este móvil (prefijo `local_`: nunca se sincronizan ni se exportan).
 * La clave se guarda aparte y solo se lee para llamar al proveedor.
 */
@Singleton
class AssistantSettings @Inject constructor(private val local: LocalSettings) {
    @Serializable
    private data class ConfigDto(
        @SerialName("proveedor") val provider: String = AiProvider.GEMINI.name,
        @SerialName("modelo") val model: String = AiConfig.DEFAULT_GEMINI_MODEL,
        @SerialName("url_base") val baseUrl: String = "",
        @SerialName("en_cada_aviso") val autoCheckIn: Boolean = true,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun observeConfig(): Flow<AiConfig> = local.observe(KEY_CONFIG).map(::decode)

    /** Solo si hay clave (sin exponerla): para mostrar «clave guardada». */
    fun observeKeyHint(): Flow<String?> = local.observe(KEY_API).map { key -> key?.takeIf { it.isNotBlank() }?.let { hint(it) } }

    suspend fun config(): AiConfig = decode(local.get(KEY_CONFIG))

    suspend fun apiKey(): String? = local.get(KEY_API)?.takeIf { it.isNotBlank() }

    suspend fun setConfig(config: AiConfig) = local.put(
        KEY_CONFIG,
        json.encodeToString(ConfigDto.serializer(), ConfigDto(config.provider.name, config.model.trim(), config.baseUrl.trim(), config.autoCheckIn)),
    )

    suspend fun setApiKey(key: String?) = local.put(KEY_API, key?.trim()?.takeIf { it.isNotEmpty() })

    private fun decode(text: String?): AiConfig {
        val dto = text?.let { runCatching { json.decodeFromString(ConfigDto.serializer(), it) }.getOrNull() } ?: ConfigDto()
        return AiConfig(
            provider = AiProvider.entries.firstOrNull { it.name == dto.provider } ?: AiProvider.GEMINI,
            model = dto.model,
            baseUrl = dto.baseUrl,
            autoCheckIn = dto.autoCheckIn,
        )
    }

    companion object {
        const val KEY_CONFIG = "ai_config"
        const val KEY_API = "ai_key"

        /** «••••1234»: los últimos 4 caracteres, para reconocerla sin mostrarla. */
        fun hint(key: String): String = "••••" + key.trim().takeLast(4)
    }
}
