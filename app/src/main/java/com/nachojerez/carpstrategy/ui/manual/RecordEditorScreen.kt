package com.nachojerez.carpstrategy.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualIssue
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing

/** Grupos del formulario (DISENO.md §5.3). Solo se muestran los campos válidos para el tipo. */
private val FIELD_GROUPS: List<Pair<Int, List<ManualField>>> = listOf(
    R.string.editor_group_water to listOf(ManualField.WATER_TEMP_SURFACE, ManualField.WATER_TEMP_BOTTOM, ManualField.BOTTOM_DEPTH),
    R.string.editor_group_air to listOf(ManualField.AIR_TEMPERATURE, ManualField.PRESSURE_MSL, ManualField.RELATIVE_HUMIDITY, ManualField.CLOUD_COVER),
    R.string.editor_group_wind to listOf(ManualField.WIND_SPEED, ManualField.WIND_GUSTS),
    R.string.editor_group_rain to listOf(ManualField.PRECIPITATION, ManualField.DAILY_PRECIPITATION),
    R.string.editor_group_reservoir to listOf(ManualField.RESERVOIR_VOLUME, ManualField.RESERVOIR_PERCENT, ManualField.RESERVOIR_ELEVATION),
)

/** Alta y edición de un registro: todo es opcional salvo la fecha/hora y la fuente. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordEditorScreen(
    state: EditorState,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onValue: (ManualField, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Text(
                stringResource(if (state.id == 0L) R.string.editor_title_new else R.string.editor_title_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        }
        Caption(stringResource(R.string.editor_hint))

        CarpCard {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !state.daily,
                    onClick = { onChange { it.copy(daily = false) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.editor_type_hourly)) }
                SegmentedButton(
                    selected = state.daily,
                    onClick = { onChange { it.copy(daily = true) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.editor_type_daily)) }
            }
            if (state.daily) {
                OutlinedTextField(
                    value = state.date,
                    onValueChange = { text -> onChange { it.copy(date = text) } },
                    label = { Text(stringResource(R.string.editor_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = state.time,
                    onValueChange = { text -> onChange { it.copy(time = text) } },
                    label = { Text(stringResource(R.string.editor_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.source,
                onValueChange = { text -> onChange { it.copy(source = text) } },
                label = { Text(stringResource(R.string.editor_source)) },
                supportingText = { Text(stringResource(R.string.editor_source_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FIELD_GROUPS.forEach { (title, fields) ->
            val visible = fields.filter { it.allowedFor(state.daily) }
            val showWind = title == R.string.editor_group_wind && !state.daily
            val showTurbidity = title == R.string.editor_group_water && !state.daily
            if (visible.isNotEmpty() || showWind) {
                SectionTitle(stringResource(title))
                CarpCard {
                    if (showWind) WindDirectionSelector(state.values[ManualField.WIND_DIRECTION].orEmpty()) { onValue(ManualField.WIND_DIRECTION, it) }
                    visible.forEach { field -> NumberField(field, state.values[field].orEmpty()) { onValue(field, it) } }
                    if (showTurbidity) TurbiditySelector(state.values[ManualField.TURBIDITY].orEmpty()) { onValue(ManualField.TURBIDITY, it) }
                }
            }
        }

        SectionTitle(stringResource(R.string.editor_notes))
        OutlinedTextField(
            value = state.notes,
            onValueChange = { text -> onChange { it.copy(notes = text) } },
            label = { Text(stringResource(R.string.editor_notes)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        IssueList(state.issues)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        onDelete?.let {
            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NumberField(field: ManualField, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(stringResource(field.labelRes())) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (field.integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Dirección de procedencia en 8 rumbos. Pulsar el rumbo elegido lo borra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindDirectionSelector(value: String, onValue: (String) -> Unit) {
    val current = value.replace(',', '.').toDoubleOrNull()?.let(Formatting::compass)
    Text(stringResource(R.string.editor_wind_direction), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        COMPASS_POINTS_DEG.forEach { deg ->
            val label = Formatting.compass(deg)
            val selected = label == current
            FilterChip(
                selected = selected,
                onClick = { onValue(if (selected) "" else Formatting.number(deg, 0)) },
                label = { Text(label) },
            )
        }
    }
    Caption(stringResource(R.string.editor_wind_direction_hint))
}

/** Turbidez de 1 (muy clara) a 5 (muy turbia). Pulsar el valor elegido lo borra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TurbiditySelector(value: String, onValue: (String) -> Unit) {
    Text(stringResource(R.string.field_turbidez), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (1..5).forEach { level ->
            val selected = value.trim() == "$level"
            FilterChip(
                selected = selected,
                onClick = { onValue(if (selected) "" else "$level") },
                label = { Text("$level") },
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
    Caption(stringResource(R.string.editor_turbidity_hint))
}

@Composable
fun IssueList(issues: List<ManualIssue>) {
    issues.forEach { issue ->
        Text(
            issueText(issue),
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.code.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
