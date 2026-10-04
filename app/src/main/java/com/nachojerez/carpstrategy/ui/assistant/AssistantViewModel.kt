package com.nachojerez.carpstrategy.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.assistant.AiConfig
import com.nachojerez.carpstrategy.data.assistant.AiException
import com.nachojerez.carpstrategy.data.assistant.Assistant
import com.nachojerez.carpstrategy.data.assistant.AssistantSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val config: AiConfig = AiConfig(),
    /** «••••1234» si hay clave; la clave nunca llega a la pantalla. */
    val keyHint: String? = null,
    val test: TestState = TestState.Idle,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val settings: AssistantSettings,
    private val assistant: Assistant,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val test = MutableStateFlow<TestState>(TestState.Idle)

    val uiState: StateFlow<AssistantUiState> =
        combine(settings.observeConfig(), settings.observeKeyHint(), test) { config, hint, t -> AssistantUiState(config, hint, t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    fun saveConfig(config: AiConfig) {
        test.value = TestState.Idle
        viewModelScope.launch { settings.setConfig(config) }
    }

    fun saveKey(key: String) {
        test.value = TestState.Idle
        viewModelScope.launch { settings.setApiKey(key) }
    }

    fun deleteKey() {
        test.value = TestState.Idle
        viewModelScope.launch { settings.setApiKey(null) }
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
