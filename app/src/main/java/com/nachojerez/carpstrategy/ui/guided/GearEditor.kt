package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.data.assistant.AiException
import com.nachojerez.carpstrategy.data.assistant.AiUsageStore
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.advice.BaitShelf
import com.nachojerez.carpstrategy.domain.advice.BaitUsage
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiUsage
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.pendingSheets
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/** Resultado de pedir la ficha de un cebo a la IA. */
sealed interface BaitSheetResult {
    data class Ok(val item: GearItem) : BaitSheetResult
    data object NoKey : BaitSheetResult
    /** [detail]: lo que dijo Google (código y mensaje), si lo dijo. */
    data class Failed(val error: String?, val issues: List<AiIssue>, val detail: String? = null) : BaitSheetResult
}

/**
 * Cambios en «Mi equipo» desde cualquier pantalla (fase 7.13: Estrategia y Mi equipo): guardar,
 * borrar, «hoy lo llevo» y la ficha del cebo con la IA (marca, nombre y enlace; con Gemini busca
 * en Google y lee el enlace).
 */
@Singleton
class GearEditor @Inject constructor(
    private val settings: SettingsRepository,
    private val assistant: Assistant,
    private val assistantSettings: AssistantSettings,
    private val usage: AiUsageStore,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun save(draft: GearDraft): GearItem? {
        if (!draft.isValid) return null
        val item = draft.toItem { UUID.randomUUID().toString() }
        settings.setGear(settings.observeGear().first().upsert(item))
        return item
    }

    suspend fun delete(id: String) = settings.setGear(settings.observeGear().first().filterNot { it.id == id })

    /** «Empezar de cero hoy»: ningún cebo marcado como «lo llevo hoy». */
    suspend fun clearToday() = settings.setGear(BaitShelf.clearToday(settings.observeGear().first()))

    /** Un cebo del diario pasa a tu lista y a lo que llevas hoy. */
    suspend fun addFromDiary(usage: BaitUsage) =
        settings.setGear(settings.observeGear().first() + BaitShelf.fromUsage(usage, UUID.randomUUID().toString()))

    suspend fun toggleToday(id: String) =
        settings.setGear(settings.observeGear().first().map { if (it.id == id) it.copy(available = !it.available) else it })

    /** Guarda el borrador, pide la ficha y, si es válida, la guarda en el cebo. */
    suspend fun fetchSheet(draft: GearDraft): BaitSheetResult {
        val item = save(draft) ?: return BaitSheetResult.Failed(null, listOf(AiIssue.EMPTY_TEXT))
        return sheetFor(item)
    }

    /** Fichas hechas sin búsqueda que ahora se pueden rehacer (fase 7.15). */
    fun observePendingSheets(): Flow<List<GearItem>> =
        combine(settings.observeGear(), observeSearchAllowed()) { gear, allowed -> gear.pendingSheets(allowed) }

    /** Quedan búsquedas; si se agotaron, pasa sola a true cuando se renueva la cuota (fase 7.17.1). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearchAllowed(): Flow<Boolean> = usage.observeSearchBlockedUntil().transformLatest { until ->
        val wait = until?.let { Duration.between(clock.instant(), it) }
        if (wait == null || wait.isNegative || wait.isZero) {
            emit(true)
        } else {
            emit(false)
            delay(wait.toMillis())
            emit(true)
        }
    }

    /**
     * Rehace las fichas pendientes una a una (fase 7.15). Se para si falta la clave o Google sigue
     * sin dejar (cuota o sin conexión): el resto acabaría igual. Devuelve cuántas salieron con
     * búsqueda o leyendo la tienda.
     */
    suspend fun refreshPendingSheets(): Int {
        var improved = 0
        for (item in observePendingSheets().first()) {
            if (item.url.isBlank() && !usage.searchAllowed(clock.instant())) continue
            when (val result = sheetFor(item)) {
                is BaitSheetResult.Ok -> if (result.item.sheet?.searched == true) improved++
                BaitSheetResult.NoKey -> break
                is BaitSheetResult.Failed -> if (result.error == QUOTA || result.error == OFFLINE) break
            }
        }
        return improved
    }

    private suspend fun sheetFor(item: GearItem): BaitSheetResult {
        val key = assistantSettings.apiKey() ?: return BaitSheetResult.NoKey
        val now = clock.instant()
        val searchAllowed = usage.searchAllowed(now)
        val outcome = withContext(io) { assistant.baitSheet(assistantSettings.config(), key, item, now, searchAllowed) }
        val sheet = outcome.value ?: return BaitSheetResult.Failed(outcome.exchange.error, outcome.exchange.issues, outcome.exchange.errorDetail)
        // Google dijo que no quedan búsquedas: no se vuelve a intentar hasta que se renueve la cuota.
        if (searchAllowed && item.url.isBlank() && sheet.searchError == QUOTA) usage.blockSearchUntil(AiUsage.nextReset(now))
        // Se relee por si el usuario cambió algo mientras la IA contestaba.
        val current = settings.observeGear().first()
        val updated = (current.firstOrNull { it.id == item.id } ?: item).withSheet(sheet)
        settings.setGear(current.upsert(updated))
        return BaitSheetResult.Ok(updated)
    }

    private companion object {
        val QUOTA = AiException.Quota::class.simpleName
        val OFFLINE = AiException.Offline::class.simpleName
    }
}
