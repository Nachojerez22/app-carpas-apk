package com.nachojerez.carpstrategy.ui.manual

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualIssue
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualDataScreen(onBack: () -> Unit, viewModel: ManualDataViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var exampleCopied by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.manual_intro), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::newRecord) { Text(stringResource(R.string.action_add)) }
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text(stringResource(R.string.action_import))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = viewModel.exampleJson()
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("carpstrategy-datos", json)))
                        exampleCopied = true
                    }
                }) { Text(stringResource(R.string.action_copy_example)) }
            }
            if (exampleCopied) {
                Text(stringResource(R.string.example_copied), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.records.isEmpty()) {
            item { Text(stringResource(R.string.manual_empty)) }
        }
        items(state.records, key = { it.id }) { record ->
            RecordCard(record, onEdit = { viewModel.edit(record) }, onDelete = { viewModel.askDelete(record) })
        }
    }

    state.editor?.let { editor ->
        EditorDialog(
            state = editor,
            onChange = viewModel::updateEditor,
            onValue = viewModel::setValue,
            onSave = viewModel::saveEditor,
            onCancel = viewModel::cancelEditor,
        )
    }
    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            text = { Text(stringResource(R.string.manual_delete_confirm)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    ImportDialog(state.import, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImport)
}

@Composable
private fun RecordCard(record: ManualRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(record.period.display(), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.manual_source, record.source), style = MaterialTheme.typography.bodySmall)
            Text(
                originText(record),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.values.toSortedMap().forEach { (field, value) ->
                Text(stringResource(R.string.manual_value, stringResource(field.labelRes()), field.format(value)))
            }
            record.notes?.let { Text(stringResource(R.string.manual_notes, it), style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun EditorDialog(
    state: EditorState,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onValue: (ManualField, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (state.id == 0L) R.string.editor_title_new else R.string.editor_title_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.daily,
                        onClick = { onChange { it.copy(daily = false) } },
                        label = { Text(stringResource(R.string.editor_type_hourly)) },
                    )
                    FilterChip(
                        selected = state.daily,
                        onClick = { onChange { it.copy(daily = true) } },
                        label = { Text(stringResource(R.string.editor_type_daily)) },
                    )
                }
                if (state.daily) {
                    OutlinedTextField(
                        value = state.date,
                        onValueChange = { text -> onChange { it.copy(date = text) } },
                        label = { Text(stringResource(R.string.editor_date)) },
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        value = state.time,
                        onValueChange = { text -> onChange { it.copy(time = text) } },
                        label = { Text(stringResource(R.string.editor_time)) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = state.source,
                    onValueChange = { text -> onChange { it.copy(source = text) } },
                    label = { Text(stringResource(R.string.editor_source)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.editor_hint), style = MaterialTheme.typography.bodySmall)
                state.fields.forEach { field ->
                    OutlinedTextField(
                        value = state.values[field].orEmpty(),
                        onValueChange = { onValue(field, it) },
                        label = { Text(stringResource(field.labelRes())) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { text -> onChange { it.copy(notes = text) } },
                    label = { Text(stringResource(R.string.editor_notes)) },
                )
                IssueList(state.issues)
            }
        },
        confirmButton = { Button(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ImportDialog(state: ImportState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ImportState.Idle -> Unit
        ImportState.Reading -> AlertDialog(
            onDismissRequest = {},
            text = { Text(stringResource(R.string.import_reading)) },
            confirmButton = {},
        )
        is ImportState.ReadError -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.import_read_error, state.message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        )
        is ImportState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.import_done, state.count)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        )
        is ImportState.Preview -> {
            val preview = state.preview
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_preview_title, state.fileName)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (preview.errors.isEmpty()) {
                            val hourly = preview.records.count { it.period is RecordPeriod.At }
                            Text(
                                stringResource(
                                    R.string.import_preview_summary,
                                    preview.records.size,
                                    hourly,
                                    preview.records.size - hourly,
                                ),
                            )
                            Text(
                                stringResource(R.string.import_unverified),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(stringResource(R.string.import_errors_title), color = MaterialTheme.colorScheme.error)
                            preview.errors.forEach { Text(issueText(it), style = MaterialTheme.typography.bodySmall) }
                        }
                        if (preview.warnings.isNotEmpty()) {
                            Text(stringResource(R.string.import_warnings_title))
                            preview.warnings.forEach { Text(issueText(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                },
                confirmButton = {
                    if (preview.canImport) {
                        Button(onClick = onConfirm) {
                            Text(stringResource(R.string.action_confirm_import, preview.records.size))
                        }
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun IssueList(issues: List<ManualIssue>) {
    issues.forEach { issue ->
        Text(
            issueText(issue),
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.code.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
