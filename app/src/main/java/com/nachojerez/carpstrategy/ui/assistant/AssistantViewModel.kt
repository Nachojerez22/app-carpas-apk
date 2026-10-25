package com.nachojerez.carpstrategy.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.assistant.AiConfig
import com.nachojerez.carpstrategy.data.assistant.AiException
import com.nachojerez.carpstrategy.data.assistant.AiUsageStore
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.assistant.AiLimits
import com.nachojerez.carpstrategy.domain.assistant.UsageDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado de «Probar conexión». */
sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data object Ok : TestState
    data class Failed(val error: AiException) : TestState
}

data class AssistantUiState(
    /** False hasta leer los ajustes guardados: antes, el formulario no debe rellenarse ni guardarse. */
    val ready: Boolean = false,
    val config: AiConfig = AiConfig(),
    /** «••••1234» si hay clave; la clave nunca llega a la pantalla. */
    val keyHint: String? = null,
    /** La clave está también en tu cuenta de Google (la tienes en todos tus móviles). */
    val keyInAccount: Boolean = false,
    val test: TestState = TestState.Idle,
    /** Modelos disponibles con tu clave (null = aún no se han pedido). */
    val models: List<String>? = null,
    val loadingModels: Boolean = false,
    val modelsError: AiException? = null,
    /** Consumo de la IA de todos tus móviles (fase 7.14) y tus límites diarios. */
    val usage: List<UsageDay> = emptyList(),
    val limits: AiLimits = AiLimits(),
    val now: Instant = Instant.EPOCH,
    /** Hasta cuándo no quedan búsquedas en Google (fase 7.15). */
    val searchBlockedUntil: Instant? = null,
)

private data class UsageState(val days: List<UsageDay>, val limits: AiLimits, val now: Instant, val searchBlockedUntil: Instant?)

private data class KeyState(val hint: String?, val inAccount: Boolean)

private data class ModelsState(val list: List<String>? = null, val loading: Boolean = false, val error: AiException? = null)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val settings: AssistantSettings,
    private val assistant: Assistant,
    private val usageStore: AiUsageStore,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val test = MutableStateFlow<TestState>(TestState.Idle)
    private val models = MutableStateFlow(ModelsState())

    /** Hora para el día de cuota y la renovación; cada minuto basta. */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    val uiState: StateFlow<AssistantUiState> =
        combine(
            settings.observeConfig(),
            combine(settings.observeKeyHint(), settings.observeInAccount(), ::KeyState),
            test,
            models,
            combine(usageStore.observeDays(), usageStore.observeLimits(), ticker, usageStore.observeSearchBlockedUntil(), ::UsageState),
        ) { config, key, t, m, u ->
            AssistantUiState(true, config, key.hint, key.inAccount, t, m.list, m.loading, m.error, u.days, u.limits, u.now, u.searchBlockedUntil)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    fun saveLimits(limits: AiLimits) {
        viewModelScope.launch { usageStore.setLimits(limits) }
    }

    fun saveConfig(config: AiConfig) {
        test.value = TestState.Idle
        viewModelScope.launch { settings.setConfig(config) }
    }

    fun saveKey(key: String, inAccount: Boolean) {
        test.value = TestState.Idle
        viewModelScope.launch { settings.setApiKey(key, inAccount) }
    }

    fun setKeyInAccount(enabled: Boolean) {
        viewModelScope.launch { settings.setInAccount(enabled) }
    }

    fun deleteKey() {
        test.value = TestState.Idle
        viewModelScope.launch { settings.deleteApiKey() }
    }

    /** Pide al proveedor la lista de modelos que admite tu clave. */
    fun loadModels() {
        if (models.value.loading) return
        models.value = ModelsState(loading = true)
        viewModelScope.launch {
            val key = settings.apiKey()
            val config = settings.config()
            val result = if (key == null) Result.failure(AiException.NotConfigured()) else withContext(io) { assistant.models(config, key) }
            models.value = ModelsState(
                list = result.getOrNull(),
                error = result.exceptionOrNull() as? AiException,
            )
        }
    }

    /** Elegir un modelo de la lista: se guarda y se vuelve a probar. */
    fun useModel(model: String) {
        viewModelScope.launch {
            settings.setConfig(settings.config().copy(model = model))
            test()
        }
    }

    fun test() {
        if (test.value == TestState.Running) return
        test.value = TestState.Running
        viewModelScope.launch {
            val key = settings.apiKey()
            val config = settings.config()
            val error = if (key == null) AiException.NotConfigured() else withContext(io) { assistant.test(config, key) }
            test.value = error?.let { TestState.Failed(it) } ?: TestState.Ok
        }
    }
}
