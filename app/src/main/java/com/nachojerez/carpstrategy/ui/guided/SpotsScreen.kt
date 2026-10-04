package com.nachojerez.carpstrategy.ui.guided

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.diary.zoneLabelRes
import com.nachojerez.carpstrategy.ui.theme.Spacing

/** «Mis puestos»: estructura, profundidad y distancia medidas por el usuario y orientación de la orilla. */
@Composable
fun SpotsScreen(onBack: () -> Unit, viewModel: SpotsViewModel = hiltViewModel()) {
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<SpotDraft?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(stringResource(R.string.spots_title), stringResource(R.string.nav_place))
        }
        item { Caption(stringResource(R.string.spots_intro)) }
        item {
            CarpCard {
                if (spots.isEmpty()) Caption(stringResource(R.string.spots_empty))
                spots.forEach { spot ->
                    Column(Modifier.fillMaxWidth().clickable { draft = SpotDraft.of(spot) }.padding(vertical = Spacing.xs)) {
                        Text(spot.name, style = MaterialTheme.typography.titleSmall)
                        Caption(spotSummary(spot))
                    }
                }
            }
            OutlinedButton(onClick = { draft = SpotDraft() }, modifier = Modifier.padding(top = Spacing.sm)) {
                Text(stringResource(R.string.spots_add))
            }
        }
    }

    draft?.let { current ->
        SpotDialog(
            draft = current,
            onChange = { draft = it },
            onSave = {
                viewModel.save(current)
                draft = null
            },
            onDelete = current.id?.let { id -> { viewModel.delete(id); draft = null } },
            onDismiss = { draft = null },
        )
    }
}

/** «Primera caída · 3,5 m de fondo · a 40 m · orilla mirando al NO · Norte». */
@Composable
fun spotSummary(spot: Spot): String = listOfNotNull(
    stringResource(spot.structure.titleRes()),
    spot.depthM?.let { stringResource(R.string.spots_depth_value, Formatting.number(it)) },
    spot.distanceM?.let { stringResource(R.string.spots_distance_value, Formatting.number(it, 0)) },
    spot.facingDeg?.let { stringResource(R.string.spots_facing_value, Formatting.compass(it.toDouble())) },
    spot.zone?.let { stringResource(it.zoneLabelRes()) },
    spot.notes.takeIf { it.isNotBlank() },
).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotDialog(draft: SpotDraft, onChange: (SpotDraft) -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.id == null) R.string.spots_add else R.string.spots_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text(stringResource(R.string.spots_name)) },
                    isError = draft.name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.spots_structure), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SpotStructure.entries.forEach { s ->
                        FilterChip(selected = draft.structure == s, onClick = { onChange(draft.copy(structure = s)) }, label = { Text(stringResource(s.titleRes())) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = draft.depth,
                        onValueChange = { onChange(draft.copy(depth = it)) },
                        label = { Text(stringResource(R.string.spots_depth)) },
                        isError = !draft.depthValid,
                        supportingText = if (!draft.depthValid) { { Text(stringResource(R.string.spots_invalid_number)) } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.distance,
                        onValueChange = { onChange(draft.copy(distance = it)) },
                        label = { Text(stringResource(R.string.spots_distance)) },
                        isError = !draft.distanceValid,
                        supportingText = if (!draft.distanceValid) { { Text(stringResource(R.string.spots_invalid_number)) } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.spots_facing), style = MaterialTheme.typography.titleSmall)
                Caption(stringResource(R.string.spots_facing_help))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = draft.facingDeg == null, onClick = { onChange(draft.copy(facingDeg = null)) }, label = { Text(stringResource(R.string.spots_facing_none)) })
                    Spots.FACINGS.forEach { deg ->
                        FilterChip(selected = draft.facingDeg == deg, onClick = { onChange(draft.copy(facingDeg = deg)) }, label = { Text(Formatting.compass(deg.toDouble())) })
                    }
                }
                Text(stringResource(R.string.spots_zone), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = draft.zone == null, onClick = { onChange(draft.copy(zone = null)) }, label = { Text(stringResource(R.string.spots_zone_none)) })
                    FishingZone.entries.forEach { z ->
                        FilterChip(selected = draft.zone == z, onClick = { onChange(draft.copy(zone = z)) }, label = { Text(stringResource(z.zoneLabelRes())) })
                    }
                }
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { onChange(draft.copy(notes = it)) },
                    label = { Text(stringResource(R.string.spots_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = draft.isValid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_delete)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
