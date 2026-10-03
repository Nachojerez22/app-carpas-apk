package com.nachojerez.carpstrategy.ui.diary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.journal.JournalSummary
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CardBorder
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.EvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.strategy.labelRes
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.format.DateTimeFormatter

private val CARD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Formatting.SPANISH)
private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Formatting.SPANISH)

/** Con menos sesiones, la comparación por valoración previa no es concluyente (§10). */
private const val MIN_SESSIONS_FOR_COMPARISON = 20

/**
 * Pestaña Diario: resumen del mes, sesión en curso, tarjetas de sesión y, a pantalla completa,
 * la ficha de cada sesión.
 */
@Composable
fun DiaryScreen(viewModel: DiaryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteRequest by viewModel.deleteRequest.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val exportName = stringResource(R.string.diary_export_file_name)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreFrom)
    }

    val editor = state.editor
    if (editor != null) {
        BackHandler(onBack = viewModel::close)
        SessionEditorScreen(
            editor = editor,
            recentCatchZones = state.recentCatchZones,
            now = state.now,
            onChange = viewModel::update,
            onSave = viewModel::save,
            onCancel = viewModel::close,
            onDelete = editor.original?.let { session -> { viewModel.askDelete(session) } },
        )
    } else {
        DiaryList(
            state = state,
            onStart = viewModel::startNow,
            onOpen = viewModel::open,
            onFinish = viewModel::finish,
            onExport = { exporter.launch(exportName) },
            onRestore = { restorer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
    }

    deleteRequest?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            text = { Text(stringResource(R.string.diary_delete_confirm)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    when (val status = exportStatus) {
        ExportStatus.Idle -> Unit
        is ExportStatus.Done -> AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            text = { Text(stringResource(R.string.diary_export_done, status.count)) },
            confirmButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.action_ok)) } },
        )
        is ExportStatus.Restored -> AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            text = { Text(stringResource(R.string.diary_restore_done, status.added, status.total)) },
            confirmButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.action_ok)) } },
        )
        ExportStatus.NotADiary -> AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            text = { Text(stringResource(R.string.diary_restore_invalid)) },
            confirmButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.action_ok)) } },
        )
        is ExportStatus.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            text = { Text(stringResource(R.string.diary_export_failed, status.message)) },
            confirmButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun DiaryList(
    state: DiaryUiState,
    onStart: () -> Unit,
    onOpen: (Session) -> Unit,
    onFinish: (Session) -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    val year = state.now.atZone(Formatting.MADRID).year
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ScreenHeader(
                    stringResource(R.string.diary_title),
                    stringResource(R.string.diary_overline_season, year, state.yearSummary.sessions),
                )
            }
            item { MonthSummary(state) }
            state.ongoing?.let { ongoing ->
                item { OngoingCard(ongoing, onOpen = { onOpen(ongoing) }, onFinish = { onFinish(ongoing) }) }
            }
            val finished = state.sessions.filterNot { it.isOngoing }
            if (finished.isEmpty() && state.ongoing == null && !state.isLoading) {
                item {
                    CarpCard {
                        Text(stringResource(R.string.diary_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.diary_empty_text))
                    }
                }
            }
            if (finished.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.diary_sessions_title)) }
                items(finished, key = { it.id }) { session -> SessionCard(session, onClick = { onOpen(session) }) }
            }
            if (state.byBand.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.diary_accuracy_title), subtitle = stringResource(R.string.diary_accuracy_subtitle)) }
                item { AccuracyCard(state) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diary_export)) }
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diary_restore)) }
                    Caption(stringResource(R.string.diary_export_hint))
                }
            }
        }
        if (state.ongoing == null) {
            ExtendedFloatingActionButton(
                onClick = onStart,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.diary_new_session)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
            )
        }
    }
}

@Composable
private fun MonthSummary(state: DiaryUiState) {
    val month = state.month?.let { MONTH.format(it) }.orEmpty()
    val s = state.monthSummary
    CarpCard {
        Row(Modifier.fillMaxWidth()) {
            Stat("${s.sessions}", stringResource(R.string.diary_stat_sessions, month), Modifier.weight(1f))
            Stat(Formatting.number(s.rodHours, 0), stringResource(R.string.diary_stat_rod_hours), Modifier.weight(1f))
            Stat("${s.catches}", stringResource(R.string.diary_stat_catches), Modifier.weight(1f))
            Stat("${s.blanks}", stringResource(R.string.diary_stat_blanks), Modifier.weight(1f))
        }
        Caption(
            s.catchesPerRodHour?.let { stringResource(R.string.diary_rate, Formatting.number(it, 2)) }
                ?: stringResource(R.string.diary_rate_none),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Caption(label)
    }
}

@Composable
private fun OngoingCard(session: Session, onOpen: () -> Unit, onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CarpCard(containerColor = cs.primaryContainer) {
        Text(stringResource(R.string.diary_ongoing_title), style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
        Text(
            stringResource(R.string.diary_ongoing_since, Formatting.clock(session.start), zoneText(session)),
            color = cs.onPrimaryContainer,
        )
        session.prediction?.band?.let {
            Caption(stringResource(R.string.diary_app_said, capitalized(stringResource(it.labelRes()))), color = cs.onPrimaryContainer)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onFinish) { Text(stringResource(R.string.diary_finish)) }
            OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.diary_open_sheet)) }
        }
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CarpCard(
        modifier = Modifier.clickable(onClick = onClick),
        border = if (session.isBlank) CardBorder.Dashed else CardBorder.Normal,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(capitalized(CARD_DATE.format(session.start.atZone(Formatting.MADRID))), style = MaterialTheme.typography.titleMedium)
                Caption("${Formatting.clock(session.start)}–${Formatting.clock(session.end)} · ${zoneText(session)}")
            }
            when {
                session.catches.isNotEmpty() -> Pill(
                    pluralCatches(session.catches.size),
                    cs.primaryContainer,
                    cs.onPrimaryContainer,
                )
                session.isBlank -> Pill(stringResource(R.string.diary_blank), cs.surfaceVariant, cs.onSurfaceVariant)
                else -> Pill(stringResource(R.string.diary_no_result), CarpTheme.colors.warnContainer, CarpTheme.colors.onWarnContainer)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("${session.bites}", stringResource(R.string.diary_bites), Modifier.weight(1f))
            Stat("${session.catches.size}", stringResource(R.string.diary_stat_catches), Modifier.weight(1f))
            Stat("${session.losses}", stringResource(R.string.diary_losses), Modifier.weight(1f))
            Stat(Formatting.number(session.rodHours, 1), stringResource(R.string.diary_stat_rod_hours), Modifier.weight(1f))
        }
        HorizontalDivider(color = cs.outlineVariant)
        Text(
            session.prediction?.let { p ->
                if (p.blocked) {
                    stringResource(R.string.diary_app_said_blocked)
                } else {
                    stringResource(R.string.diary_app_said, p.band?.let { capitalized(stringResource(it.labelRes())) } ?: stringResource(R.string.diary_app_no_band))
                }
            } ?: stringResource(R.string.diary_app_said_none),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

/** Capturas por hora-caña según lo que dijo la app antes: ¿acierta con tus datos? */
@Composable
private fun AccuracyCard(state: DiaryUiState) {
    val total = state.byBand.values.sumOf { it.sessions }
    CarpCard {
        FavorabilityBand.entries.reversed().forEach { band ->
            val summary: JournalSummary = state.byBand[band] ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(capitalized(stringResource(band.labelRes())), modifier = Modifier.weight(1f))
                Text(
                    stringResource(
                        R.string.diary_accuracy_row,
                        summary.sessions,
                        summary.catchesPerRodHour?.let { Formatting.number(it, 2) } ?: Formatting.number(null),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (total < MIN_SESSIONS_FOR_COMPARISON) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                EvidenceBadge(EvidenceKind.IndividualVariability, short = true)
                Caption(stringResource(R.string.diary_accuracy_few, total, MIN_SESSIONS_FOR_COMPARISON), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun zoneText(session: Session): String {
    val zone = session.zone?.let { stringResource(it.zoneLabelRes()) }
    return listOfNotNull(zone, session.zoneDetail.ifBlank { null }).joinToString(" · ").ifBlank { stringResource(R.string.diary_no_zone) }
}

@Composable
private fun pluralCatches(n: Int): String =
    if (n == 1) stringResource(R.string.diary_one_catch) else stringResource(R.string.diary_n_catches, n)

/** Primera letra en mayúscula ("favorables" → "Favorables"). */
fun capitalized(text: String): String = text.replaceFirstChar { it.titlecase(Formatting.SPANISH) }
