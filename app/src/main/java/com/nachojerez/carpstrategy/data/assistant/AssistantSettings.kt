package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.data.userdata.SettingDao
import com.nachojerez.carpstrategy.data.userdata.SettingEntity
import com.nachojerez.carpstrategy.domain.guided.AiCheckInMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ajustes del asistente. La clave se guarda en este móvil (prefijo `local_`) y, si el usuario lo
 * elige, también en su cuenta de Google ([ACCOUNT_KEY], fase 7.11) para tenerla en todos sus
 * móviles; manda la de la cuenta. Proveedor y modelo van en la cuenta ([ACCOUNT_CONFIG]). Nada de
 * esto se exporta; la clave solo se lee para llamar al proveedor y nunca se muestra entera.
 */
@Singleton
class AssistantSettings @Inject constructor(private val local: LocalSettings, private val dao: SettingDao) {
    @Serializable
    private data class ConfigDto(
        @SerialName("proveedor") val provider: String = AiProvider.GEMINI.name,
        @SerialName("modelo") val model: String = AiConfig.DEFAULT_GEMINI_MODEL,
        @SerialName("url_base") val baseUrl: String = "",
        /** Versiones anteriores a la 0.21.0 (el otro móvil puede seguir leyéndolo). */
        @SerialName("en_cada_aviso") val autoCheckIn: Boolean = true,
        @SerialName("modo_avisos") val checkInMode: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** La de la cuenta (la misma en todos tus móviles) o, si no hay, la de este móvil. */
    fun observeConfig(): Flow<AiConfig> = combine(dao.observe(ACCOUNT_CONFIG), local.observe(KEY_CONFIG)) { account, here -> decode(account ?: here) }

    /** Solo si hay clave (sin exponerla): para mostrar «clave guardada». */
    fun observeKeyHint(): Flow<String?> = observeKey().map { key -> key?.let(::hint) }

    /** La clave está guardada en tu cuenta de Google. */
    fun observeInAccount(): Flow<Boolean> = dao.observe(ACCOUNT_KEY).map { !it.isNullOrBlank() }

    suspend fun config(): AiConfig = decode(dao.observe(ACCOUNT_CONFIG).first() ?: local.get(KEY_CONFIG))

    suspend fun apiKey(): String? = observeKey().first()

    suspend fun setConfig(config: AiConfig) = dao.upsert(
        SettingEntity(
            ACCOUNT_CONFIG,
            json.encodeToString(ConfigDto.serializer(), ConfigDto(config.provider.name, config.model.trim(), config.baseUrl.trim(), config.checkInMode != AiCheckInMode.NEVER, config.checkInMode.name)),
        ),
    )

    /**
     * Guarda la clave en este móvil y, con [inAccount], también en tu cuenta. Siempre queda una
     * copia en el móvil donde se escribe: si otro móvil la quita de la cuenta, aquí sigue.
     */
    suspend fun setApiKey(key: String, inAccount: Boolean) {
        val clean = key.trim().takeIf { it.isNotEmpty() } ?: return
        local.put(KEY_API, clean)
        if (inAccount) dao.upsert(SettingEntity(ACCOUNT_KEY, clean)) else dao.delete(ACCOUNT_KEY)
    }

    /**
     * Activa o desactiva la clave en la cuenta. Al desactivarla, este móvil se queda con una copia
     * (los móviles que solo la tenían por la cuenta la pierden).
     */
    suspend fun setInAccount(enabled: Boolean) {
        val key = apiKey() ?: return
        if (enabled) {
            dao.upsert(SettingEntity(ACCOUNT_KEY, key))
        } else {
            local.put(KEY_API, key)
            dao.delete(ACCOUNT_KEY)
        }
    }

    /** Borra la clave de este móvil y de tu cuenta. */
    suspend fun deleteApiKey() {
        local.put(KEY_API, null)
        dao.delete(ACCOUNT_KEY)
    }

    private fun observeKey(): Flow<String?> = combine(dao.observe(ACCOUNT_KEY), local.observe(KEY_API)) { account, here ->
        account?.takeIf { it.isNotBlank() } ?: here?.takeIf { it.isNotBlank() }
    }

    private fun decode(text: String?): AiConfig {
        val dto = text?.let { runCatching { json.decodeFromString(ConfigDto.serializer(), it) }.getOrNull() } ?: ConfigDto()
        return AiConfig(
            provider = AiProvider.entries.firstOrNull { it.name == dto.provider } ?: AiProvider.GEMINI,
            model = dto.model,
            baseUrl = dto.baseUrl,
            // «En cada aviso» de antes pasa a «si cambia algo» (gasta mucha menos cuota).
            checkInMode = AiCheckInMode.entries.firstOrNull { it.name == dto.checkInMode }
                ?: if (dto.autoCheckIn) AiCheckInMode.ON_CHANGE else AiCheckInMode.NEVER,
        )
    }

    companion object {
        /** En este móvil (con el prefijo `local_`): versiones anteriores y copia de la clave. */
        const val KEY_CONFIG = "ai_config"
        const val KEY_API = "ai_key"

        /** En tu cuenta de Google (sin `local_`: se sincronizan con Drive, nunca se exportan). */
        const val ACCOUNT_CONFIG = "ai_config_cuenta"
        const val ACCOUNT_KEY = "ai_key_cuenta"

        /** «••••1234»: los últimos 4 caracteres, para reconocerla sin mostrarla. */
        fun hint(key: String): String = "••••" + key.trim().takeLast(4)
    }
}
