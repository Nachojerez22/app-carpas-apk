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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.assistant.AiException
import com.nachojerez.carpstrategy.domain.advice.GroundbaitAmounts
import com.nachojerez.carpstrategy.domain.assistant.AiUsage
import com.nachojerez.carpstrategy.domain.guided.BaitSheet
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Instant

/**
 * «Mi equipo»: cebos (también engodo, harinas y líquidos) y montajes. Cada cebo con marca, qué
 * lleva, enlace de tienda, ficha de la IA y si lo llevas hoy (fase 7.13); las propuestas eligen
 * entre los que llevas hoy.
 */
@Composable
fun GearScreen(onBack: () -> Unit, viewModel: GearViewModel = hiltViewModel()) {
    val gear by viewModel.gear.collectAsStateWithLifecycle()
    val normalKg by viewModel.groundbaitNormalKg.collectAsStateWithLifecycle()
    val sheet by viewModel.sheetState.collectAsStateWithLifecycle()
    val pending by viewModel.pendingSheets.collectAsStateWithLifecycle()
    val refresh by viewModel.pendingRefresh.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<GearDraft?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(stringResource(R.string.gear_title), stringResource(R.string.gear_overline))
        }
        // Fase 7.15: fichas hechas sin búsqueda que ya se pueden rehacer (lo pulsas tú: gasta cuota).
        if (pending > 0 || refresh != PendingRefresh.Idle) {
            item { PendingSheetsCard(pending, refresh, viewModel::refreshPendingSheets) }
        }
        GearCategory.entries.forEach { category ->
            item {
                SectionTitle(stringResource(if (category == GearCategory.BAIT) R.string.gear_baits else R.string.gear_rigs))
                CarpCard {
                    val items = gear.filter { it.category == category }
                    if (items.isEmpty()) Caption(stringResource(R.string.gear_none))
                    items.forEach { item ->
                        if (category == GearCategory.BAIT) {
                            BaitRow(item, onToggle = { viewModel.toggleToday(item.id) }, onEdit = { viewModel.clearSheetState(); draft = GearDraft.of(item) })
                        } else {
                            Row(Modifier.fillMaxWidth().clickable { draft = GearDraft.of(item) }.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, modifier = Modifier.weight(1f))
                                item.rigType?.let { Caption(stringResource(it.titleRes())) }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { viewModel.clearSheetState(); draft = GearDraft(category = category) }, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(if (category == GearCategory.BAIT) R.string.gear_add_bait else R.string.gear_add_rig))
                }
                if (category == GearCategory.BAIT) GroundbaitNormalRow(normalKg, viewModel::setGroundbaitNormalKg)
            }
        }
        item { Caption(stringResource(R.string.gear_hint)) }
    }

    draft?.let { current ->
        GearDialog(
            draft = current,
            onChange = { draft = it },
            onSave = {
                viewModel.save(current)
                draft = null
            },
            onDelete = current.id?.let { id -> { viewModel.delete(id); draft = null } },
            onDismiss = { draft = null },
            sheet = sheet,
            onFetchSheet = { viewModel.fetchSheet(current) { updated -> draft = updated } },
        )
    }
}

/** «Buscar de nuevo las fichas pendientes (N)» y cómo fue la última vez. */
@Composable
internal fun PendingSheetsCard(pending: Int, refresh: PendingRefresh, onRefresh: () -> Unit) {
    CarpCard {
        if (pending > 0) Caption(stringResource(R.string.gear_pending_hint))
        when (refresh) {
            PendingRefresh.Running -> Caption(stringResource(R.string.gear_pending_running))
            is PendingRefresh.Done -> Caption(stringResource(R.string.gear_pending_done, refresh.improved))
            PendingRefresh.Idle -> Unit
        }
        if (pending > 0) {
            OutlinedButton(onClick = onRefresh, enabled = refresh != PendingRefresh.Running) {
                Text(stringResource(R.string.gear_pending_button, pending))
            }
        }
    }
}

/** Un cebo de la lista: casilla «hoy lo llevo», nombre con marca y tipo; tocar para editar. */
@Composable
internal fun BaitRow(item: GearItem, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = item.available, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = Spacing.xs)) {
            Text(item.fullName.ifBlank { item.name }, color = if (item.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            val details = listOfNotNull(
                item.baitType?.let { stringResource(it.titleRes()) },
                item.description.takeIf { it.isNotBlank() } ?: item.sheet?.composition,
            ).joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Tu cebado «normal» en kg por caña y sesión: la base para pasar poco/normal/mucho a kilos (🟣). */
@Composable
internal fun GroundbaitNormalRow(normalKg: Double?, onSet: (Double?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(normalKg) { mutableStateOf(normalKg?.let { Formatting.number(it, 2) } ?: "") }
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.groundbait_normal_title), style = MaterialTheme.typography.titleSmall)
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    normalKg?.let { stringResource(R.string.groundbait_normal_value, Formatting.number(it, 2)) } ?: stringResource(R.string.groundbait_normal_none),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editing = true }) { Text(stringResource(if (normalKg == null) R.string.groundbait_normal_set else R.string.groundbait_normal_change)) }
            }
        } else {
            val valid = text.isBlank() || GroundbaitAmounts.parse(text) != null
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.groundbait_normal_field)) },
                isError = !valid,
                supportingText = { Text(stringResource(if (valid) R.string.groundbait_normal_help else R.string.groundbait_normal_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                TextButton(onClick = { onSet(GroundbaitAmounts.parse(text)); editing = false }, enabled = valid) { Text(stringResource(R.string.action_save)) }
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        Caption(stringResource(R.string.groundbait_normal_hint))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GearDialog(
    draft: GearDraft,
    onChange: (GearDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    sheet: SheetUiState = SheetUiState(),
    onFetchSheet: (() -> Unit)? = null,
) {
    val bait = draft.category == GearCategory.BAIT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (bait) R.string.gear_add_bait else R.string.gear_add_rig)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text(stringResource(R.string.gear_name)) },
                    isError = draft.name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (bait) {
                    OutlinedTextField(
                        value = draft.brand,
                        onValueChange = { onChange(draft.copy(brand = it)) },
                        label = { Text(stringResource(R.string.gear_brand)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.gear_type), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (bait) {
                        BaitType.entries.forEach { t ->
                            FilterChip(selected = draft.baitType == t, onClick = { onChange(draft.copy(baitType = t)) }, label = { Text(stringResource(t.titleRes())) })
                        }
                    } else {
                        RigType.entries.forEach { t ->
                            FilterChip(selected = draft.rigType == t, onClick = { onChange(draft.copy(rigType = t)) }, label = { Text(stringResource(t.titleRes())) })
                        }
                    }
                }
                if (bait) {
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { onChange(draft.copy(description = it)) },
                        label = { Text(stringResource(R.string.gear_description)) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.url,
                        onValueChange = { onChange(draft.copy(url = it)) },
                        label = { Text(stringResource(R.string.gear_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.gear_today), modifier = Modifier.weight(1f))
                        Switch(checked = draft.available, onCheckedChange = { onChange(draft.copy(available = it)) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    draft.sheet?.let { BaitSheetView(it) }
                    onFetchSheet?.let { fetch ->
                        if (sheet.running) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Caption(stringResource(R.string.gear_sheet_running))
                        } else {
                            OutlinedButton(onClick = fetch, enabled = draft.isValid) {
                                Text(stringResource(if (draft.sheet == null) R.string.gear_sheet_fetch else R.string.gear_sheet_refetch))
                            }
                        }
                        sheetResultText(sheet.result)?.let { Caption(it, color = MaterialTheme.colorScheme.error) }
                        Caption(stringResource(R.string.gear_sheet_hint))
                    }
                }
                if (draft.name.isBlank()) Caption(stringResource(R.string.gear_name_required))
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = draft.isValid && !sheet.running) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_delete)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** La ficha de la IA: composición, perfil, solubilidad, notas y fuentes, con su evidencia (🟣). */
@Composable
private fun BaitSheetView(sheet: BaitSheet) {
    // Título y evidencia arriba; el texto, a todo el ancho (con la insignia al lado quedaba en media columna).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.gear_sheet_title, Formatting.hour(sheet.updatedAt)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            AnyEvidenceBadge(sheet.evidence)
        }
        Text(sheet.composition)
        if (sheet.profile.isNotBlank()) Caption(stringResource(R.string.gear_sheet_profile, sheet.profile))
        sheet.solubility?.let { Caption(stringResource(R.string.gear_sheet_solubility, stringResource(it.titleRes()))) }
        if (sheet.notes.isNotBlank()) Caption(sheet.notes)
        if (sheet.sources.isNotEmpty()) Caption(stringResource(R.string.gear_sheet_sources, sheet.sources.joinToString("\n")))
        if (!sheet.searched) Caption(notSearchedText(sheet), color = MaterialTheme.colorScheme.error)
    }
}

/** Por qué la ficha se hizo sin buscar en Google, con lo que dijo Google si lo dijo. */
@Composable
private fun notSearchedText(sheet: BaitSheet): String {
    val reason = when (sheet.searchError) {
        QUOTA -> stringResource(R.string.gear_sheet_not_searched_quota, Formatting.clock(AiUsage.nextReset(Instant.now())))
        URL_UNREADABLE -> stringResource(R.string.gear_sheet_not_searched_url)
        null -> stringResource(R.string.gear_sheet_not_searched)
        else -> stringResource(R.string.gear_sheet_not_searched_refused)
    }
    return reason + (sheet.searchErrorDetail?.let { "\n" + stringResource(R.string.ai_error_detail, it) } ?: "")
}

/** Por qué no se pudo preparar la ficha (o null si fue bien o aún no se ha pedido). */
@Composable
private fun sheetResultText(result: BaitSheetResult?): String? {
    val res = LocalContext.current.resources
    return when (result) {
        null, is BaitSheetResult.Ok -> null
        BaitSheetResult.NoKey -> stringResource(R.string.gear_sheet_no_key)
        is BaitSheetResult.Failed -> when (result.error) {
            QUOTA -> stringResource(R.string.gear_sheet_quota)
            OFFLINE -> stringResource(R.string.gear_sheet_offline)
            FAILED -> stringResource(R.string.gear_sheet_failed_http)
            else -> stringResource(
                R.string.gear_sheet_failed,
                result.error ?: result.issues.map { res.getString(it.titleRes()) }.distinct().joinToString(", "),
            )
        } + (result.detail?.let { "\n" + stringResource(R.string.ai_error_detail, it) } ?: "")
    }
}

private val QUOTA = AiException.Quota::class.simpleName
private val OFFLINE = AiException.Offline::class.simpleName
private val FAILED = AiException.Failed::class.simpleName
private val URL_UNREADABLE = AiException.UrlUnreadable::class.simpleName
