package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Resultado de pedir la ficha de un cebo a la IA. */
sealed interface BaitSheetResult {
    data class Ok(val item: GearItem) : BaitSheetResult
    data object NoKey : BaitSheetResult
    data class Failed(val error: String?, val issues: List<AiIssue>) : BaitSheetResult
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

    suspend fun toggleToday(id: String) =
        settings.setGear(settings.observeGear().first().map { if (it.id == id) it.copy(available = !it.available) else it })

    /** Guarda el borrador, pide la ficha y, si es válida, la guarda en el cebo. */
    suspend fun fetchSheet(draft: GearDraft): BaitSheetResult {
        val item = save(draft) ?: return BaitSheetResult.Failed(null, listOf(AiIssue.EMPTY_TEXT))
        val key = assistantSettings.apiKey() ?: return BaitSheetResult.NoKey
        val outcome = withContext(io) { assistant.baitSheet(assistantSettings.config(), key, item, clock.instant()) }
        val sheet = outcome.value ?: return BaitSheetResult.Failed(outcome.exchange.error, outcome.exchange.issues)
        // Se relee por si el usuario cambió algo mientras la IA contestaba.
        val current = settings.observeGear().first()
        val updated = (current.firstOrNull { it.id == item.id } ?: item).withSheet(sheet)
        settings.setGear(current.upsert(updated))
        return BaitSheetResult.Ok(updated)
    }
}
