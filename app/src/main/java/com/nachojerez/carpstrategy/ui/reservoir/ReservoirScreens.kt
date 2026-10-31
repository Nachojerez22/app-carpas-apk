package com.nachojerez.carpstrategy.ui.reservoir

import com.nachojerez.carpstrategy.ui.components.BackButton
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirRegime
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesRules
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.titleRes
import com.nachojerez.carpstrategy.ui.place.CoordinateError
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/** Elegir embalse: el catálogo de cotos de Extremadura más Brovales y los tuyos, por cercanía. */
@Composable
fun ReservoirsScreen(onBack: () -> Unit, onOpenSheet: () -> Unit, viewModel: ReservoirsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            BackButton(onBack)
            ScreenHeader(stringResource(R.string.reservoirs_title), stringResource(R.string.nav_place))
        }
        item {
            CarpCard {
                Text(stringResource(R.string.reservoirs_active), style = MaterialTheme.typography.titleSmall)
                val active = state.active
                if (active == null) {
                    Caption(stringResource(R.string.reservoirs_none_active))
                } else {
                    Text(active.reservoir.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Caption(reservoirLine(active.reservoir))
                    OutlinedButton(onClick = onOpenSheet) { Text(stringResource(R.string.reservoir_sheet_open)) }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.reservoirs_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Caption(stringResource(R.string.reservoirs_count, state.results.size, state.total))
        }
        items(state.results, key = { it.reservoir.id }) { row ->
            val selected = state.active?.reservoir?.id == row.reservoir.id
            CarpCard(modifier = Modifier.clickable { viewModel.select(row.reservoir) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.reservoir.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Caption(reservoirLine(row.reservoir) + " · " + stringResource(R.string.reservoirs_km, Formatting.number(row.km, 0)))
                    }
                    if (selected) Text(stringResource(R.string.reservoirs_selected), color = MaterialTheme.colorScheme.primary)
                    if (row.reservoir.custom) TextButton(onClick = { viewModel.removeCustom(row.reservoir.id) }) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
        item {
            CarpCard {
                Caption(stringResource(R.string.reservoirs_custom_intro))
                OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.reservoirs_custom_add)) }
                Caption(stringResource(R.string.reservoirs_source))
            }
        }
    }

    if (adding) {
        AddReservoirDialog(
            initialLat = state.here?.latitude?.let { Formatting.number(it, 5) }.orEmpty(),
            initialLon = state.here?.longitude?.let { Formatting.number(it, 5) }.orEmpty(),
            error = state.coordinateError,
            onDismiss = { adding = false },
            onSave = { name, lat, lon -> if (viewModel.addCustom(name, lat, lon)) adding = false },
        )
    }
}

@Composable
private fun AddReservoirDialog(initialLat: String, initialLon: String, error: CoordinateError?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf(initialLat) }
    var lon by rememberSaveable { mutableStateOf(initialLon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reservoirs_custom_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.reservoirs_custom_name)) }, singleLine = true)
                OutlinedTextField(
                    lat, { lat = it }, label = { Text(stringResource(R.string.place_latitude)) }, singleLine = true,
                    isError = error == CoordinateError.LATITUDE, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    lon, { lon = it }, label = { Text(stringResource(R.string.place_longitude)) }, singleLine = true,
                    isError = error == CoordinateError.LONGITUDE, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Caption(stringResource(R.string.reservoirs_custom_hint))
            }
        },
        confirmButton = { Button(onClick = { onSave(name, lat, lon) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Municipio · coto · permiso por internet. */
@Composable
fun reservoirLine(r: Reservoir): String = listOfNotNull(
    r.municipality,
    r.coto?.let { stringResource(R.string.reservoir_coto, it) } ?: if (r.regime == ReservoirRegime.GENERAL && !r.custom) stringResource(R.string.reservoir_general) else null,
    if (r.custom) stringResource(R.string.reservoir_custom) else null,
).joinToString(" · ")

/**
 * Ficha del embalse activo: lo del listado oficial, especies (la IA propone, tú confirmas),
 * si se puede cebar y notas de estrategia con su evidencia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReservoirSheetScreen(onBack: () -> Unit, viewModel: ReservoirsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val active = state.active

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            BackButton(onBack)
            ScreenHeader(active?.reservoir?.name ?: stringResource(R.string.reservoir_sheet_title), stringResource(R.string.reservoir_sheet_title))
        }
        if (active == null) {
            item { Caption(stringResource(R.string.reservoirs_none_active)) }
            return@LazyColumn
        }
        item { CatalogCard(active) }
        item {
            SectionTitle(stringResource(R.string.reservoir_ai_title), subtitle = stringResource(R.string.reservoir_ai_subtitle))
            CarpCard {
                if (!state.aiConfigured) Caption(stringResource(R.string.reservoir_ai_no_key))
                Button(onClick = viewModel::generateSheet, enabled = state.aiConfigured && !state.aiRunning) {
                    Text(stringResource(if (active.sheet?.aiGeneratedAt == null) R.string.reservoir_ai_generate else R.string.reservoir_ai_regenerate))
                }
                if (state.aiRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.lastAi?.let { e ->
                    when {
                        e.error != null -> Caption(stringResource(R.string.reservoir_ai_error, e.error), color = MaterialTheme.colorScheme.error)
                        !e.valid -> Caption(
                            stringResource(R.string.reservoir_ai_invalid, e.issues.map { stringResource(it.titleRes()) }.distinct().joinToString(", ")),
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Caption(stringResource(R.string.reservoir_ai_done))
                    }
                }
                active.sheet?.aiGeneratedAt?.let { Caption(stringResource(R.string.reservoir_ai_when, DATE.format(it.atZone(Formatting.MADRID)))) }
                active.sheet?.summary?.let { Text(it) }
                active.sheet?.notes?.forEach { n ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("• " + n.text, Modifier.weight(1f))
                        AnyEvidenceBadge(n.evidence)
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.reservoir_species_title), subtitle = stringResource(R.string.reservoir_species_subtitle))
            CarpCard {
                if (active.sheet?.hasUnconfirmed == true) {
                    Caption(stringResource(R.string.reservoir_species_unconfirmed))
                    OutlinedButton(onClick = viewModel::confirmAll) { Text(stringResource(R.string.reservoir_species_confirm_all)) }
                }
                FishSpecies.entries.forEach { sp ->
                    val status = active.sheet?.species?.get(sp)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(sp.titleRes()), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (status != null && !status.confirmedByUser) Caption(stringResource(R.string.reservoir_species_by_ai))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilterChip(selected = status?.present == true, onClick = { viewModel.setSpecies(sp, if (status?.present == true && status.confirmedByUser) null else true) }, label = { Text(stringResource(R.string.reservoir_species_yes)) })
                            FilterChip(selected = status?.present == false, onClick = { viewModel.setSpecies(sp, if (status?.present == false && status.confirmedByUser) null else false) }, label = { Text(stringResource(R.string.reservoir_species_no)) })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item {
            val effects = SpeciesRules.effects(active.species)
            if (effects.notes.isNotEmpty()) {
                SectionTitle(stringResource(R.string.reservoir_effects_title))
                CarpCard {
                    effects.notes.forEach { (sp, evidence) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(stringResource(sp.effectRes()), Modifier.weight(1f))
                            AnyEvidenceBadge(evidence)
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.reservoir_groundbait_title))
            CarpCard {
                val allowed = active.sheet?.groundbaitAllowed
                Caption(stringResource(R.string.reservoir_groundbait_hint))
                if (active.groundbaitDoubt) Caption(stringResource(R.string.reservoir_groundbait_doubt), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = allowed == true, onClick = { viewModel.setGroundbait(true) }, label = { Text(stringResource(R.string.reservoir_groundbait_yes)) })
                    FilterChip(selected = allowed == false, onClick = { viewModel.setGroundbait(false) }, label = { Text(stringResource(R.string.reservoir_groundbait_no)) })
                    FilterChip(selected = allowed == null, onClick = { viewModel.setGroundbait(null) }, label = { Text(stringResource(R.string.reservoir_groundbait_unknown)) })
                }
                if (allowed == null && active.reservoir.waterSupplyByName) Caption(stringResource(R.string.reservoir_groundbait_by_name))
            }
        }
    }
}

@Composable
private fun CatalogCard(active: ReservoirContext) {
    val r = active.reservoir
    CarpCard {
        Text(stringResource(R.string.reservoir_catalog_title), style = MaterialTheme.typography.titleSmall)
        Caption(reservoirLine(r))
        if (r.coto != null) {
            Text(stringResource(if (r.onlinePermit) R.string.reservoir_online_permit_yes else R.string.reservoir_online_permit_no))
            val members = r.postsMembers
            val others = r.postsOthers
            if (members != null && others != null && (members > 0 || others > 0)) Text(stringResource(R.string.reservoir_posts, members, others))
        }
        Caption(stringResource(R.string.reservoir_coordinates, Formatting.number(r.point.latitude, 4), Formatting.number(r.point.longitude, 4)))
        if (!active.isBrovales) Caption(stringResource(R.string.reservoir_not_brovales))
    }
}

private val DATE = DateTimeFormatter.ofPattern("d/M/yyyy")

@StringRes
fun FishSpecies.titleRes(): Int = when (this) {
    FishSpecies.CARP -> R.string.species_carp
    FishSpecies.BARBEL -> R.string.species_barbel
    FishSpecies.NASE -> R.string.species_nase
    FishSpecies.TENCH -> R.string.species_tench
    FishSpecies.CRUCIAN -> R.string.species_crucian
    FishSpecies.BULLHEAD -> R.string.species_bullhead
    FishSpecies.CATFISH -> R.string.species_catfish
    FishSpecies.BLACK_BASS -> R.string.species_black_bass
    FishSpecies.PIKE -> R.string.species_pike
    FishSpecies.PIKEPERCH -> R.string.species_pikeperch
    FishSpecies.SUNFISH -> R.string.species_sunfish
    FishSpecies.BLEAK -> R.string.species_bleak
    FishSpecies.CRAYFISH -> R.string.species_crayfish
}

/** Qué cambia en la pesca de la carpa por cada especie (§5.11). */
@StringRes
fun FishSpecies.effectRes(): Int = when (this) {
    FishSpecies.CARP -> R.string.species_effect_carp
    FishSpecies.BARBEL -> R.string.species_effect_barbel
    FishSpecies.NASE -> R.string.species_effect_nase
    FishSpecies.TENCH -> R.string.species_effect_tench
    FishSpecies.CRUCIAN -> R.string.species_effect_crucian
    FishSpecies.BULLHEAD -> R.string.species_effect_bullhead
    FishSpecies.CATFISH -> R.string.species_effect_catfish
    FishSpecies.BLACK_BASS, FishSpecies.PIKE, FishSpecies.PIKEPERCH -> R.string.species_effect_predator
    FishSpecies.SUNFISH -> R.string.species_effect_sunfish
    FishSpecies.BLEAK -> R.string.species_effect_bleak
    FishSpecies.CRAYFISH -> R.string.species_effect_crayfish
}
