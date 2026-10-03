package com.nachojerez.carpstrategy.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Fulfilled
import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.SessionValidator
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CardBorder
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.EvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.strategy.labelRes
import com.nachojerez.carpstrategy.ui.strategy.titleRes
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

private val COMPUTED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM 'a las' HH:mm", Formatting.SPANISH)

/** Ficha de sesión (DISENO.md §5.4): horas, resultado con bolo explícito, puesto, equipo y valoración previa. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionEditorScreen(
    editor: SessionEditor,
    recentCatchZones: Map<FishingZone, Instant>,
    now: Instant,
    onChange: ((SessionForm) -> SessionForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val form = editor.form
    val errors = editor.fieldErrors
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
                stringResource(if (editor.original == null) R.string.session_title_new else R.string.session_title_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        }

        // Horas.
        CarpCard {
            TextField(form.date, stringResource(R.string.session_date), FormField.DATE in errors) { v -> onChange { it.copy(date = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextField(form.startTime, stringResource(R.string.session_start), FormField.START in errors, Modifier.weight(1f)) { v -> onChange { it.copy(startTime = v) } }
                TextField(form.endTime, stringResource(R.string.session_end), FormField.END in errors, Modifier.weight(1f)) { v -> onChange { it.copy(endTime = v) } }
            }
            Caption(stringResource(R.string.session_end_hint))
            Caption(stringResource(R.string.session_legal_note))
        }

        // Resultado.
        SectionTitle(stringResource(R.string.session_result_title))
        CarpCard {
            Counter(stringResource(R.string.diary_bites), form.bites) { v -> onChange { it.copy(bites = v) } }
            Counter(stringResource(R.string.diary_losses), form.losses) { v -> onChange { it.copy(losses = v) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.session_catches, form.catches.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange { it.copy(catches = it.catches + CatchInput(), blank = false) } }) {
                    Text(stringResource(R.string.session_add_catch))
                }
            }
            form.catches.forEachIndexed { index, c ->
                CatchRow(index, c, errors, onChange)
            }
            if (form.catches.isEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.session_blank_question), style = MaterialTheme.typography.titleSmall)
                Caption(stringResource(R.string.session_blank_hint))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = form.blank == true,
                        onClick = { onChange { it.copy(blank = true) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.fulfilled_yes)) }
                    SegmentedButton(
                        selected = form.blank == false,
                        onClick = { onChange { it.copy(blank = false) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.fulfilled_no)) }
                }
            }
        }

        // Puesto.
        SectionTitle(stringResource(R.string.session_spot_title), subtitle = stringResource(R.string.session_spot_subtitle))
        CarpCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FishingZone.entries.forEach { zone ->
                    val selected = form.zone == zone
                    FilterChip(
                        selected = selected,
                        onClick = { onChange { it.copy(zone = if (selected) null else zone) } },
                        label = { Text(stringResource(zone.zoneLabelRes())) },
                    )
                }
            }
            form.zone?.let { zone -> recentCatchZones[zone] }?.let { last ->
                if (editor.original == null || editor.original.zone != form.zone) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(R.string.session_rotate_warning, Duration.between(last, now).toDays().coerceAtLeast(0).toInt()),
                            color = CarpTheme.colors.warnStrong,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        EvidenceBadge(EvidenceKind.Strong, short = true)
                    }
                }
            }
            TextField(form.zoneDetail, stringResource(R.string.session_zone_detail), false) { v -> onChange { it.copy(zoneDetail = v) } }
            TextField(form.depth, stringResource(R.string.session_depth), FormField.DEPTH in errors, keyboard = KeyboardType.Decimal) { v -> onChange { it.copy(depth = v) } }
        }

        // Equipo.
        SectionTitle(stringResource(R.string.session_gear_title))
        CarpCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextField(form.rods, stringResource(R.string.session_rods), FormField.RODS in errors, Modifier.weight(1f), KeyboardType.Number) { v -> onChange { it.copy(rods = v) } }
                TextField(form.rodHours, stringResource(R.string.session_rod_hours), FormField.ROD_HOURS in errors, Modifier.weight(1f), KeyboardType.Decimal) { v -> onChange { it.copy(rodHours = v) } }
            }
            Caption(stringResource(R.string.session_rod_hours_hint, SessionValidator.MAX_LEGAL_RODS))
            TextField(form.bait, stringResource(R.string.session_bait), false) { v -> onChange { it.copy(bait = v) } }
            TextField(form.rig, stringResource(R.string.session_rig), false) { v -> onChange { it.copy(rig = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextField(form.groundbait, stringResource(R.string.session_groundbait), FormField.GROUNDBAIT in errors, Modifier.weight(1f), KeyboardType.Decimal) { v -> onChange { it.copy(groundbait = v) } }
                TextField(form.otherAnglers, stringResource(R.string.session_other_anglers), FormField.OTHER_ANGLERS in errors, Modifier.weight(1f), KeyboardType.Number) { v -> onChange { it.copy(otherAnglers = v) } }
            }
        }

        // Lo que dijo la app antes de salir (copia fija).
        SectionTitle(stringResource(R.string.session_prediction_title))
        PredictionCard(editor.original?.prediction, isNew = editor.original == null)
        if (editor.original != null) {
            Text(stringResource(R.string.session_fulfilled_question), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Fulfilled.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = form.fulfilled == option,
                        onClick = { onChange { it.copy(fulfilled = if (form.fulfilled == option) null else option) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Fulfilled.entries.size),
                    ) { Text(stringResource(option.fulfilledLabelRes())) }
                }
            }
        }

        // Notas.
        SectionTitle(stringResource(R.string.editor_notes))
        OutlinedTextField(
            value = form.notes,
            onValueChange = { v -> onChange { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.session_notes_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        if (errors.isNotEmpty()) {
            Text(stringResource(R.string.session_field_errors), color = MaterialTheme.colorScheme.error)
        }
        editor.issues.forEach { issue ->
            Text(
                sessionIssueText(issue),
                color = if (issue.code.isError) MaterialTheme.colorScheme.error else CarpTheme.colors.warnStrong,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (editor.warningsAcknowledged) Caption(stringResource(R.string.session_save_anyway))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        onDelete?.let {
            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.session_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PredictionCard(prediction: PredictionSnapshot?, isNew: Boolean) {
    val cs = MaterialTheme.colorScheme
    if (prediction == null) {
        CarpCard(border = CardBorder.Dashed) {
            Text(
                stringResource(if (isNew) R.string.session_prediction_pending else R.string.session_prediction_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    CarpCard(containerColor = cs.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                when {
                    prediction.blocked -> stringResource(R.string.strategy_blocked)
                    prediction.band != null -> capitalized(stringResource(prediction.band.labelRes()))
                    else -> stringResource(R.string.diary_app_no_band)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (prediction.blocked) AnyEvidenceBadge(Evidence.REGULATION)
        }
        val limiting = prediction.limitingLevel?.let { stringResource(R.string.session_prediction_limiting, it.order, stringResource(it.titleRes())) }
        Caption(
            listOfNotNull(
                stringResource(R.string.session_prediction_computed, COMPUTED_AT.format(prediction.computedAt.atZone(Formatting.MADRID))),
                limiting,
            ).joinToString(" · "),
        )
        Caption(stringResource(R.string.session_prediction_fixed))
    }
}

@Composable
private fun CatchRow(index: Int, c: CatchInput, errors: Set<FormField>, onChange: ((SessionForm) -> SessionForm) -> Unit) {
    CarpCard(contentPadding = Spacing.sm, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(R.string.session_catch_number, index + 1), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onChange { f -> f.copy(catches = f.catches.filterIndexed { i, _ -> i != index }) } }) {
                Text(stringResource(R.string.action_delete))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextField(c.time, stringResource(R.string.session_catch_time), FormField.CATCH_TIME in errors, Modifier.weight(1f)) { v ->
                onChange { f -> f.copy(catches = f.catches.mapIndexed { i, x -> if (i == index) x.copy(time = v) else x }) }
            }
            TextField(c.weight, stringResource(R.string.session_catch_weight), FormField.CATCH_WEIGHT in errors, Modifier.weight(1f), KeyboardType.Decimal) { v ->
                onChange { f -> f.copy(catches = f.catches.mapIndexed { i, x -> if (i == index) x.copy(weight = v) else x }) }
            }
            TextField(c.rod, stringResource(R.string.session_catch_rod), FormField.CATCH_ROD in errors, Modifier.width(80.dp), KeyboardType.Number) { v ->
                onChange { f -> f.copy(catches = f.catches.mapIndexed { i, x -> if (i == index) x.copy(rod = v) else x }) }
            }
        }
        TextField(c.species, stringResource(R.string.session_catch_species), false) { v ->
            onChange { f -> f.copy(catches = f.catches.mapIndexed { i, x -> if (i == index) x.copy(species = v) else x }) }
        }
    }
}

/** Contador con − y + (picadas, pérdidas). */
@Composable
private fun Counter(label: String, value: Int, onValue: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onValue((value - 1).coerceAtLeast(0)) }, enabled = value > 0) { Text("−") }
        Text("$value", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(40.dp))
        FilledTonalIconButton(onClick = { onValue(value + 1) }) { Text("+") }
    }
}

@Composable
private fun TextField(
    value: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}
