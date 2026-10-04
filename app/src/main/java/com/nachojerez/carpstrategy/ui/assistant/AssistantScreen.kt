package com.nachojerez.carpstrategy.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.assistant.AiConfig
import com.nachojerez.carpstrategy.data.assistant.AiException
import com.nachojerez.carpstrategy.data.assistant.AiProvider
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.theme.Spacing

/**
 * Asistente de IA: proveedor, modelo y clave (solo en este móvil). La clave se escribe una vez y
 * después solo se ve «••••1234».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(onBack: () -> Unit, viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var provider by rememberSaveable { mutableStateOf(AiProvider.GEMINI) }
    var model by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var auto by rememberSaveable { mutableStateOf(true) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var key by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.config) {
        if (!loaded) {
            provider = state.config.provider
            model = state.config.model
            baseUrl = state.config.baseUrl
            auto = state.config.autoCheckIn
            loaded = true
        }
    }
    fun save() = viewModel.saveConfig(AiConfig(provider, model, baseUrl, auto))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(stringResource(R.string.ai_settings_title), stringResource(R.string.nav_place))
        }
        item {
            CarpCard {
                Text(stringResource(R.string.ai_settings_intro))
                Caption(stringResource(R.string.ai_settings_privacy))
            }
        }
        item {
            CarpCard {
                Text(stringResource(R.string.ai_provider), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = provider == AiProvider.GEMINI,
                        onClick = {
                            provider = AiProvider.GEMINI
                            if (model.isBlank()) model = AiConfig.DEFAULT_GEMINI_MODEL
                        },
                        label = { Text(stringResource(R.string.ai_provider_gemini)) },
                    )
                    FilterChip(selected = provider == AiProvider.OPENAI_COMPATIBLE, onClick = { provider = AiProvider.OPENAI_COMPATIBLE }, label = { Text(stringResource(R.string.ai_provider_openai)) })
                }
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.ai_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Caption(stringResource(R.string.ai_model_hint))
                if (provider == AiProvider.OPENAI_COMPATIBLE) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.ai_base_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.ai_auto), modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Button(onClick = ::save, enabled = model.isNotBlank()) { Text(stringResource(R.string.action_save)) }
            }
        }
        item {
            CarpCard {
                Text(stringResource(R.string.ai_key), style = MaterialTheme.typography.titleSmall)
                Text(state.keyHint?.let { stringResource(R.string.ai_key_saved, it) } ?: stringResource(R.string.ai_key_none))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.ai_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        onClick = {
                            save()
                            viewModel.saveKey(key)
                            key = ""
                        },
                        enabled = key.isNotBlank(),
                    ) { Text(stringResource(R.string.ai_key_save)) }
                    if (state.keyHint != null) OutlinedButton(onClick = viewModel::deleteKey) { Text(stringResource(R.string.ai_key_delete)) }
                }
            }
        }
        item {
            CarpCard {
                OutlinedButton(onClick = viewModel::test, enabled = state.keyHint != null && state.test != TestState.Running) {
                    Text(stringResource(if (state.test == TestState.Running) R.string.ai_testing else R.string.ai_test))
                }
                when (val t = state.test) {
                    TestState.Ok -> Text(stringResource(R.string.ai_test_ok))
                    is TestState.Failed -> Text(aiErrorText(t.error), color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
        }
        item {
            CarpCard {
                Text(stringResource(R.string.ai_models_title), style = MaterialTheme.typography.titleSmall)
                Caption(stringResource(R.string.ai_models_hint))
                OutlinedButton(onClick = viewModel::loadModels, enabled = state.keyHint != null && !state.loadingModels) {
                    Text(stringResource(if (state.loadingModels) R.string.ai_models_loading else R.string.ai_models_load))
                }
                state.modelsError?.let { Text(aiErrorText(it), color = MaterialTheme.colorScheme.error) }
                state.models?.let { list ->
                    if (list.isEmpty()) Caption(stringResource(R.string.ai_models_empty))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        list.forEach { name ->
                            FilterChip(
                                selected = name == state.config.model,
                                onClick = {
                                    model = name
                                    viewModel.useModel(name)
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun aiErrorText(error: AiException): String = when (error) {
    is AiException.Unauthorized -> stringResource(R.string.ai_error_unauthorized)
    is AiException.Quota -> stringResource(R.string.ai_error_quota)
    is AiException.Offline -> stringResource(R.string.ai_error_offline)
    is AiException.Failed -> stringResource(R.string.ai_error_failed, error.code)
    is AiException.Empty -> stringResource(R.string.ai_error_empty)
    is AiException.NotConfigured -> stringResource(R.string.ai_error_not_configured)
}
