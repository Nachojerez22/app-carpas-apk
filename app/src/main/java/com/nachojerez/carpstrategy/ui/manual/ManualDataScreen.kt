package com.nachojerez.carpstrategy.ui.manual

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CardBorder
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.components.SourceBadge
import com.nachojerez.carpstrategy.ui.components.WarningChip
import com.nachojerez.carpstrategy.ui.components.WarningType
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.SourceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.format.DateTimeFormatter

private val GROUP_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Formatting.SPANISH)

/**
 * Pestaña Datos: registros manuales e importados. El formulario y la importación se abren a
 * pantalla completa sobre la lista (el botón atrás vuelve a ella).
 */
@Composable
fun DataScreen(
    viewModel: ManualDataViewModel = hiltViewModel(),
    startWithNewRecord: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val exportName = stringResource(R.string.data_export_file_name)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var newRecordHandled by rememberSaveable { mutableStateOf(false) }
    if (startWithNewRecord && !newRecordHandled) {
        LaunchedEffect(Unit) {
            newRecordHandled = true
            viewModel.newRecord()
        }
    }

    val editor = state.editor
    when {
        editor != null -> {
            BackHandler(onBack = viewModel::cancelEditor)
            RecordEditorScreen(
                state = editor,
                onChange = viewModel::updateEditor,
                onValue = viewModel::setValue,
                onSave = viewModel::saveEditor,
                onCancel = viewModel::cancelEditor,
                onDelete = state.records.firstOrNull { it.id == editor.id }?.let { record -> { viewModel.askDelete(record) } },
            )
        }
        showImport -> {
            val close = {
                viewModel.dismissImport()
                showImport = false
            }
            BackHandler(onBack = close)
            ImportScreen(viewModel = viewModel, state = state.import, onClose = close)
        }
        else -> RecordList(
            records = state.records,
            onNew = viewModel::newRecord,
            onImport = { showImport = true },
            onExport = { exporter.launch(exportName) },
            onEdit = viewModel::edit,
        )
    }

    exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            text = {
                Text(
                    result.error?.let { stringResource(R.string.diary_export_failed, it) }
                        ?: stringResource(R.string.data_export_done, result.count),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            text = { Text(stringResource(R.string.manual_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmDelete()
                    viewModel.cancelEditor()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordList(
    records: List<ManualRecord>,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onEdit: (ManualRecord) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(RecordFilter.ALL) }
    val groups = groupRecordsByDay(records, filter)
    val imported = records.count { it.origin is ManualOrigin.Imported }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ScreenHeader(stringResource(R.string.data_title), stringResource(R.string.data_overline)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(
                        RecordFilter.ALL to R.string.data_filter_all,
                        RecordFilter.HOURLY to R.string.data_filter_hourly,
                        RecordFilter.DAILY to R.string.data_filter_daily,
                    )
                    options.forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = filter == option,
                            onClick = { filter = option },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onImport) { Text(stringResource(R.string.action_import)) }
                    if (records.isNotEmpty()) {
                        OutlinedButton(onClick = onExport) { Text(stringResource(R.string.data_export)) }
                    }
                }
            }
            if (imported > 0) {
                item {
                    WarningChip(WarningType.Unverified, stringResource(R.string.warning_unverified, imported), painterResource(R.drawable.ic_nav_about))
                }
            }
            if (groups.isEmpty()) {
                item {
                    CarpCard {
                        Text(stringResource(R.string.data_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.manual_intro))
                    }
                }
            }
            groups.forEach { (day, dayRecords) ->
                item(key = "day-$day") { SectionTitle(GROUP_DAY.format(day).replaceFirstChar { it.uppercase() }) }
                item(key = "records-$day") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        dayRecords.forEach { record -> RecordRow(record, onClick = { onEdit(record) }) }
                    }
                }
            }
            item { Caption(stringResource(R.string.data_footer)) }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(R.string.data_new_record)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
        )
    }
}

@Composable
private fun RecordRow(record: ManualRecord, onClick: () -> Unit) {
    val imported = record.origin is ManualOrigin.Imported
    CarpCard(
        border = if (imported) CardBorder.Dashed else CardBorder.Normal,
        dashedColor = MaterialTheme.colorScheme.secondary,
        contentPadding = Spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SourceBadge(SourceKind.Manual)
            Column(Modifier.weight(1f)) {
                Text(record.period.display(), style = MaterialTheme.typography.titleSmall)
                Caption(record.source)
            }
            Pill(
                stringResource(if (imported) R.string.data_origin_unverified else R.string.data_origin_typed),
                if (imported) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                if (imported) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val labels = record.values.keys.associateWith { stringResource(it.labelRes()) }
        Text(
            record.values.toSortedMap().entries.joinToString(" · ") { (field, value) -> "${labels.getValue(field)}: ${field.format(value)}" },
            style = MaterialTheme.typography.bodyMedium,
        )
        Caption(originText(record))
        record.notes?.let { Caption(it) }
        TextButton(onClick = onClick) { Text(stringResource(R.string.action_edit)) }
    }
}
