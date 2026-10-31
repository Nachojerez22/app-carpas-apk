package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantPrompts
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.data.assistant.KnowledgeAsset
import com.nachojerez.carpstrategy.data.userdata.GuidedJson
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.GearEditor
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Preparar la próxima salida» (fase 7.15; desde la fase 7.25 se pulsa en Preparar). Singleton con
 * su propio ámbito: si cambias de pestaña mientras prepara, sigue. Lo que sale queda en los ajustes
 * locales (plan de la IA, su clave y la foto de la preparación), que Estrategia lee.
 */
@Singleton
class OutingPreparer @Inject constructor(
    private val observeRawWeather: ObserveRawWeatherUseCase,
    private val rulesRepository: RulesRepository,
    private val weather: WeatherRepository,
    private val settings: SettingsRepository,
    private val journal: JournalRepository,
    private val clock: Clock,
    private val assistant: Assistant,
    private val assistantSettings: AssistantSettings,
    private val localSettings: LocalSettings,
    private val knowledge: KnowledgeAsset,
    private val gearEditor: GearEditor,
    private val inputsSource: StrategyInputsSource,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val prepFlow = MutableStateFlow(PrepUiState())
    val state: StateFlow<PrepUiState> = prepFlow
    private val chosenFlow = MutableStateFlow<Instant?>(null)

    /** Día y hora elegidos para la salida (fase 7.27); null = la de siempre ([nextOuting]). */
    val chosen: StateFlow<Instant?> = chosenFlow

    fun choose(target: Instant) {
        chosenFlow.value = target
    }

    /**
     * Con cobertura: previsión nueva, plan de la IA para la salida elegida (día y hora; el embalse, puesto,
     * cañas y cebos elegidos) y fichas de cebos pendientes. Cada paso puede fallar sin parar los
     * demás: lo que no se pueda se queda como estaba.
     */
    fun prepare() {
        if (prepFlow.value.running) return
        scope.launch {
            try {
                doPrepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un fallo inesperado no deja el botón bloqueado: se puede volver a intentar.
                prepFlow.value = PrepUiState()
            }
        }
    }

    private suspend fun doPrepare() {
        run {
            val place = settings.observeLocation().first()
            val now = clock.instant()
            val days = outingDays(now, place.point, Formatting.MADRID)
            val target = chooseOuting(chosenFlow.value, days, nextOuting(now, place.point, Formatting.MADRID)) ?: return
            prepFlow.value = PrepUiState(step = PrepStep.FORECAST)
            val forecastOk = weather.refreshForecast(place.point) is RefreshOutcome.Success
            weather.refreshObservations(place.point)

            prepFlow.value = PrepUiState(step = PrepStep.PLAN)
            val base = buildStrategyState(observeRawWeather(place.point).first(), rulesRepository.load(), target, place.point, journal.observeSessions().first())
            val state = withInputs(base, inputsSource.observe().first(), target)
            val key = planKey(state)
            val input = planInput(state, state.planRods, state.gear, target)
            val apiKey = assistantSettings.apiKey()
            var prepared: AiOutcome<AiPlan>? = null
            val (planResult, detail) = when {
                key == null || input == null -> PrepPlan.NOTHING to null
                apiKey == null -> PrepPlan.NO_KEY to null
                else -> {
                    val outcome = withContext(io) {
                        assistant.plan(
                            assistantSettings.config(), apiKey, AssistantPrompts.planState(input), planContext(state, state.planRods, state.gear, target),
                            knowledge.forPlan(state.reservoir?.isBrovales ?: true),
                        )
                    }
                    if (outcome.value != null) {
                        prepared = outcome
                        localSettings.put(GuidedSessionManager.KEY_PREPARED_PLAN, GuidedJson.encodeAi(outcome.exchange))
                        localSettings.put(KEY_PREPARED_KEY, key)
                        PrepPlan.READY to null
                    } else {
                        PrepPlan.FAILED to outcome.exchange.errorDetail
                    }
                }
            }

            prepFlow.value = PrepUiState(step = PrepStep.SHEETS)
            val improved = gearEditor.refreshPendingSheets()
            val pending = gearEditor.observePendingSheets().first().size
            val result = PrepResult(target, forecastOk, planResult, detail, improved, pending)
            // Fase 7.19: se guarda lo que salió para poder abrirlo y analizarlo después.
            val dayPlan = dayPlanFor(state, PlanUiState(prepared = prepared, preparedKey = key))
            val outing = preparedOutingOf(state, dayPlan, prepared?.value?.advice.orEmpty(), result, clock.instant())
            localSettings.put(KEY_PREPARED_OUTING, PreparedOutingJson.encode(outing))
            prepFlow.value = PrepUiState(result = result)
        }
    }
}
