package com.nachojerez.carpstrategy.ui.manual

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Importar JSON `carpstrategy-datos`: pegar, abrir un archivo o copiar el ejemplo para pedírselo
 * a Claude. Vista previa por registro con los errores de cada campo; todo o nada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(viewModel: ManualDataViewModel, state: ImportState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var text by rememberSaveable { mutableStateOf("") }
    var exampleCopied by rememberSaveable { mutableStateOf(false) }
    val pastedName = stringResource(R.string.import_pasted_name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Caption(stringResource(R.string.import_intro))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = {
                scope.launch {
                    clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let {
                        text = it.toString()
                        viewModel.dismissImport()
                    }
                }
            }) { Text(stringResource(R.string.import_paste)) }
            OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text(stringResource(R.string.import_file))
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val json = viewModel.exampleJson()
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("carpstrategy-datos", json)))
                    exampleCopied = true
                }
            }) { Text(stringResource(R.string.action_copy_example)) }
        }
        if (exampleCopied) Caption(stringResource(R.string.example_copied))

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (state !is ImportState.Idle) viewModel.dismissImport()
            },
            label = { Text(stringResource(R.string.import_editor_label)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        )
        Button(onClick = { viewModel.onTextPasted(text, pastedName) }, enabled = text.isNotBlank()) {
            Text(stringResource(R.string.import_check))
        }

        when (state) {
            ImportState.Idle -> Unit
            ImportState.Reading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Caption(stringResource(R.string.import_reading))
            }
            is ImportState.ReadError -> CarpCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Text(stringResource(R.string.import_read_error, state.message), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            is ImportState.Done -> CarpCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(R.string.import_done, state.count), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Button(onClick = onClose) { Text(stringResource(R.string.import_back_to_data)) }
            }
            is ImportState.Preview -> ImportPreviewSection(state, onConfirm = viewModel::confirmImport)
        }
    }
}

@Composable
private fun ImportPreviewSection(state: ImportState.Preview, onConfirm: () -> Unit) {
    val preview = state.preview
    SectionTitle(stringResource(R.string.import_preview_title, state.fileName))
    if (preview.errors.isNotEmpty()) {
        CarpCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
            Text(stringResource(R.string.import_errors_title), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
            preview.errors.forEach { Text(issueText(it), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (preview.warnings.isNotEmpty()) {
        CarpCard {
            Text(stringResource(R.string.import_warnings_title), style = MaterialTheme.typography.titleSmall)
            preview.warnings.forEach { Caption(issueText(it)) }
        }
    }
    if (preview.records.isNotEmpty()) {
        val hourly = preview.records.count { it.period is RecordPeriod.At }
        CarpCard {
            Text(
                stringResource(R.string.import_preview_summary, preview.records.size, hourly, preview.records.size - hourly),
                style = MaterialTheme.typography.titleSmall,
            )
            preview.records.take(PREVIEW_LIMIT).forEachIndexed { i, record ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(record.period.display(), style = MaterialTheme.typography.labelLarge)
                val labels = record.values.keys.associateWith { stringResource(it.labelRes()) }
                Caption(record.values.toSortedMap().entries.joinToString(" · ") { (field, value) -> "${labels.getValue(field)}: ${field.format(value)}" })
            }
            if (preview.records.size > PREVIEW_LIMIT) {
                Caption(stringResource(R.string.import_preview_more, preview.records.size - PREVIEW_LIMIT))
            }
        }
        Caption(stringResource(R.string.import_unverified), color = MaterialTheme.colorScheme.secondary)
    }
    if (preview.canImport) {
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_confirm_import, preview.records.size))
        }
    }
}

private const val PREVIEW_LIMIT = 20
