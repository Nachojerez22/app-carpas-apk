package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.data.userdata.SettingDao
import com.nachojerez.carpstrategy.data.userdata.SettingEntity
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.assistant.AiCall
import com.nachojerez.carpstrategy.domain.assistant.AiLimits
import com.nachojerez.carpstrategy.domain.assistant.AiUsage
import com.nachojerez.carpstrategy.domain.assistant.UsageDay
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Consumo de la IA (fase 7.14). Cada móvil apunta sus consultas en su propio ajuste
 * `ai_usage_<id del móvil>` (se sincroniza; como cada móvil solo escribe el suyo, no hay choques)
 * y la pantalla suma los de todos: la cuota es de la clave, que pueden compartir. Tus límites van
 * en `ai_limits` (también se sincroniza).
 */
@Singleton
class AiUsageStore @Inject constructor(
    private val dao: SettingDao,
    private val local: LocalSettings,
    @IoDispatcher io: CoroutineDispatcher,
) : AiUsageRecorder {
    // Si falla al guardar, se pierde ese apunte y nada más (sin cerrar la app).
    private val scope = CoroutineScope(SupervisorJob() + io + CoroutineExceptionHandler { _, _ -> })
    private val mutex = Mutex()

    /** Lo llama [AiClient] desde su hilo: se guarda aparte, en orden, sin bloquear la consulta. */
    override fun record(call: AiCall) {
        scope.launch {
            mutex.withLock {
                val key = PREFIX + deviceId()
                val days = AiUsageJson.decodeDays(dao.observe(key).first())
                dao.upsert(SettingEntity(key, AiUsageJson.encodeDays(AiUsage.record(days, call))))
            }
        }
    }

    /** Días de todos los móviles, sumados. */
    fun observeDays(): Flow<List<UsageDay>> = dao.observeAll()
        .map { all -> AiUsage.merge(all.filter { it.key.startsWith(PREFIX) }.map { AiUsageJson.decodeDays(it.value) }) }
        .distinctUntilChanged()

    fun observeLimits(): Flow<AiLimits> = dao.observe(KEY_LIMITS).map(AiUsageJson::decodeLimits).distinctUntilChanged()

    suspend fun setLimits(limits: AiLimits) = dao.upsert(SettingEntity(KEY_LIMITS, AiUsageJson.encodeLimits(limits)))

    /**
     * Hasta cuándo no quedan búsquedas en Google (fase 7.15): tras un «cuota agotada» de la búsqueda
     * no se vuelve a intentar hasta la renovación. Se sincroniza: la cuota es de la clave.
     */
    fun observeSearchBlockedUntil(): Flow<Instant?> =
        dao.observe(KEY_SEARCH_BLOCKED).map { it?.let { v -> runCatching { Instant.parse(v) }.getOrNull() } }.distinctUntilChanged()

    suspend fun searchAllowed(now: Instant): Boolean = observeSearchBlockedUntil().first()?.let { now >= it } ?: true

    suspend fun blockSearchUntil(until: Instant) = dao.upsert(SettingEntity(KEY_SEARCH_BLOCKED, until.toString()))

    private suspend fun deviceId(): String = local.get(KEY_DEVICE) ?: UUID.randomUUID().toString().take(8).also { local.put(KEY_DEVICE, it) }

    companion object {
        const val PREFIX = "ai_usage_"
        const val KEY_LIMITS = "ai_limits"
        const val KEY_SEARCH_BLOCKED = "ai_search_blocked_until"

        /** Id de este móvil (ajuste local, no se sincroniza). */
        const val KEY_DEVICE = "device_id"
    }
}
