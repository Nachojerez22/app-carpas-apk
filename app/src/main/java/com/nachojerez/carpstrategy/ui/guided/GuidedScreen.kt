package com.nachojerez.carpstrategy.ui.guided

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.Decision
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.GuidedMessage
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CardBorder
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.diary.zoneLabelRes
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Duration

/**
 * Sesión guiada (§5.9): plan A, avisos, respuestas rápidas y alternativas cuando no funciona.
 * Al terminar, [onFinished] abre la ficha de la sesión en el diario para anotar el resultado.
 */
@Composable
fun GuidedScreen(
    onBack: () -> Unit,
    onOpenGear: () -> Unit,
    onFinished: (Long) -> Unit,
    viewModel: GuidedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.recheckPermissions() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.recheckPermissions() }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    var selectedRod by rememberSaveable { mutableStateOf(1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(stringResource(R.string.guided_title), stringResource(R.string.guided_overline))
        }
        if (!state.canNotify) item { PermissionCard(stringResource(R.string.guided_notifications_off), null, null) }
        if (!state.exactAlarms) {
            item {
                val context = LocalContext.current
                PermissionCard(
                    stringResource(R.string.guided_exact_alarms),
                    stringResource(R.string.guided_exact_alarms_action),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
        val session = state.session
        val phase = state.phase
        if (session == null || phase == null) {
            if (!state.isLoading) {
                item {
                    StartCard(state, onOpenGear) { names, zone, groundbait ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        viewModel.start(names, zone, groundbait)
                    }
                }
            }
        } else {
            val rods = state.rods
            val rod = rods.firstOrNull { it.id == selectedRod } ?: rods.firstOrNull()
            item { HeaderCard(state, session, phase, rods.size > 1, viewModel::nothingEverywhere, viewModel::windChanged) }
            rods.forEach { r ->
                r.log.pending?.let { record ->
                    item(key = "propuesta-${r.id}") {
                        ProposalCard(r, rods.size > 1, record.proposal, phase, { viewModel.accept(r.id) }, { reason, comment -> viewModel.reject(r.id, reason, comment) })
                    }
                }
            }
            if (state.messages.isNotEmpty()) item { MessagesCard(state.messages) }
            if (rods.size > 1) {
                item {
                    val res = LocalContext.current.resources
                    Text(stringResource(R.string.guided_which_rod), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        rods.forEach { r ->
                            FilterChip(selected = rod?.id == r.id, onClick = { selectedRod = r.id }, label = { Text(res.rodLabel(r.id, r.name)) })
                        }
                    }
                }
            }
            if (rod != null) {
                item { PlanCard(session, rod, rods.size > 1, state) }
                item { RegisterCard(rod) { draft -> viewModel.checkIn(rod.id, draft) } }
            }
            item { HistoryCard(rods, phase) }
            item {
                Button(
                    onClick = { confirmFinish = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) { Text(stringResource(R.string.guided_finish)) }
            }
        }
        item { WarningsCard() }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            text = { Text(stringResource(R.string.guided_finish_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finish(onFinished)
                }) { Text(stringResource(R.string.guided_finish)) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PermissionCard(text: String, action: String?, onAction: (() -> Unit)?) {
    CarpCard(border = CardBorder.Warning) {
        Text(text)
        if (action != null && onAction != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(state: GuidedUiState, onOpenGear: () -> Unit, onStart: (List<String>, FishingZone?, GroundbaitLevel?) -> Unit) {
    var rods by rememberSaveable { mutableStateOf(2) }
    var names by rememberSaveable { mutableStateOf(listOf("", "", "")) }
    var zone by rememberSaveable { mutableStateOf<FishingZone?>(null) }
    var groundbait by rememberSaveable { mutableStateOf<GroundbaitLevel?>(null) }
    CarpCard {
        Text(stringResource(R.string.guided_intro))
        Text(stringResource(R.string.guided_rods), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (1..GuidedSessions.MAX_RODS).forEach { n -> FilterChip(selected = rods == n, onClick = { rods = n }, label = { Text("$n") }) }
        }
        Text(stringResource(R.string.guided_rods_names), style = MaterialTheme.typography.titleSmall)
        (0 until rods).forEach { i ->
            OutlinedTextField(
                value = names[i],
                onValueChange = { value -> names = names.toMutableList().also { it[i] = value } },
                label = { Text(stringResource(R.string.guided_rod_default, i + 1)) },
                placeholder = { Text(stringResource(R.string.guided_rod_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.guided_zone), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FishingZone.entries.forEach { z ->
                FilterChip(selected = zone == z, onClick = { zone = if (zone == z) null else z }, label = { Text(stringResource(z.zoneLabelRes())) })
            }
        }
        Text(stringResource(R.string.guided_groundbait_start), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GroundbaitLevel.entries.forEach { g ->
                FilterChip(selected = groundbait == g, onClick = { groundbait = if (groundbait == g) null else g }, label = { Text(stringResource(g.titleRes())) })
            }
        }
        Caption(stringResource(R.string.guided_groundbait_hint))
        Caption(
            if (state.baits + state.rigs == 0) stringResource(R.string.guided_gear_empty) else stringResource(R.string.guided_gear_summary, state.baits, state.rigs),
        )
        OutlinedButton(onClick = onOpenGear) { Text(stringResource(R.string.guided_gear_open)) }
        Button(onClick = { onStart(rodNamesFor(rods, names), zone, groundbait) }, enabled = !state.starting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.starting) R.string.guided_starting else R.string.guided_start))
        }
    }
}

@Composable
private fun HeaderCard(state: GuidedUiState, session: Session, phase: FishingPhase, severalRods: Boolean, onNothingAll: () -> Unit, onWind: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val elapsed = Duration.between(session.start, state.now).coerceAtLeast(Duration.ZERO)
    CarpCard(containerColor = cs.primaryContainer) {
        Text(
            stringResource(R.string.guided_elapsed, Formatting.clock(session.start), "${elapsed.toHours()}:${(elapsed.toMinutes() % 60).toString().padStart(2, '0')}"),
            style = MaterialTheme.typography.titleMedium,
            color = cs.onPrimaryContainer,
        )
        Text(stringResource(R.string.guided_phase, stringResource(phase.titleRes())), color = cs.onPrimaryContainer)
        Caption(
            state.nextCheckIn?.let { stringResource(R.string.guided_next_check, Formatting.clock(it)) } ?: stringResource(R.string.guided_next_none),
            color = cs.onPrimaryContainer,
        )
        state.lastSaved?.let { Caption(stringResource(R.string.guided_saved, Formatting.clock(it)), color = cs.onPrimaryContainer) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (severalRods) OutlinedButton(onClick = onNothingAll) { Text(stringResource(R.string.guided_nothing_all)) }
            OutlinedButton(onClick = onWind) { Text(stringResource(R.string.guided_wind_changed)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalCard(rod: RodUi, showRod: Boolean, proposal: Proposal, phase: FishingPhase, onAccept: () -> Unit, onReject: (RejectReason, String) -> Unit) {
    val res = LocalContext.current.resources
    var rejecting by rememberSaveable(rod.id) { mutableStateOf(false) }
    CarpCard(border = CardBorder.Warning) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val title = stringResource(R.string.guided_proposal_title)
            Text(if (showRod) "$title · ${res.rodLabel(rod.id, rod.name)}" else title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AnyEvidenceBadge(proposal.evidence)
        }
        Text(res.proposalText(proposal, phase), style = MaterialTheme.typography.bodyLarge)
        Caption(res.situationText(proposal, phase))
        proposal.rigName?.let { Caption(stringResource(R.string.guided_step_rig_name, it)) }
        if (proposal.bait != null && proposal.baitName == null) Caption(stringResource(R.string.guided_step_no_gear))
        if (proposal.forced) Pill(stringResource(R.string.guided_forced), CarpTheme.colors.warnContainer, CarpTheme.colors.onWarnContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onAccept) { Text(stringResource(R.string.guided_accept)) }
            OutlinedButton(onClick = { rejecting = true }) { Text(stringResource(R.string.guided_reject)) }
        }
    }
    if (rejecting) {
        var reason by rememberSaveable { mutableStateOf<RejectReason?>(null) }
        var comment by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = false },
            title = { Text(stringResource(R.string.guided_reject_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        RejectReason.entries.forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(stringResource(r.titleRes())) })
                        }
                    }
                    OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text(stringResource(R.string.guided_reject_comment)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reason != null,
                    onClick = {
                        reason?.let { onReject(it, comment) }
                        rejecting = false
                    },
                ) { Text(stringResource(R.string.guided_reject)) }
            },
            dismissButton = { TextButton(onClick = { rejecting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MessagesCard(messages: Set<GuidedMessage>) {
    CarpCard {
        Text(stringResource(R.string.guided_messages_title), style = MaterialTheme.typography.titleMedium)
        messages.sortedBy { it.ordinal }.forEach { message ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AnyEvidenceBadge(message.evidence)
                Text(stringResource(message.titleRes()), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlanCard(session: Session, rod: RodUi, showRod: Boolean, state: GuidedUiState) {
    val res = LocalContext.current.resources
    val segment = rod.log.current
    CarpCard {
        val title = stringResource(R.string.guided_plan_title)
        Text(if (showRod) "$title · ${res.rodLabel(rod.id, rod.name)}" else title, style = MaterialTheme.typography.titleMedium)
        res.baitText(segment.bait, segment.baitName)?.let { Text(stringResource(R.string.guided_plan_bait, it)) }
        (segment.rigName ?: session.rig.ifBlank { null }.takeIf { !showRod })?.let { Text(stringResource(R.string.guided_plan_rig, it)) }
        segment.column?.let { Text(stringResource(R.string.guided_plan_column, stringResource(it.titleRes()))) }
        rod.groundbait?.let { Text(stringResource(R.string.guided_rod_groundbait, stringResource(it.titleRes()).lowercase())) }
        Caption(stringResource(R.string.guided_plan_since, Formatting.clock(segment.start), segment.bites, segment.catches))
        if (segment.bycatch > 0) Caption(stringResource(R.string.guided_plan_bycatch, segment.bycatch))
        state.lastSaved?.let { Caption(stringResource(R.string.guided_saved, Formatting.clock(it))) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterCard(rod: RodUi, onCheckIn: (CheckInDraft) -> Unit) {
    var signals by rememberSaveable(rod.id) { mutableStateOf(SignalLevel.NONE) }
    var bait by rememberSaveable(rod.id) { mutableStateOf(BaitState.NOT_CHECKED) }
    var dialog by rememberSaveable(rod.id) { mutableStateOf<RegisterDialog?>(null) }
    fun send(draft: CheckInDraft) {
        onCheckIn(draft.copy(signals = signals, baitState = bait))
        signals = SignalLevel.NONE
        bait = BaitState.NOT_CHECKED
    }
    CarpCard {
        Text(stringResource(R.string.guided_register_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.guided_signals), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SignalLevel.entries.forEach { s -> FilterChip(selected = signals == s, onClick = { signals = s }, label = { Text(stringResource(s.titleRes())) }) }
        }
        Caption(stringResource(R.string.guided_signal_hint))
        Text(stringResource(R.string.guided_bait_state), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BaitState.entries.forEach { b -> FilterChip(selected = bait == b, onClick = { bait = b }, label = { Text(stringResource(b.titleRes())) }) }
        }
        Text(stringResource(R.string.guided_activity), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HookActivity.entries.forEach { a ->
                if (a == HookActivity.CATCH) {
                    Button(onClick = { dialog = RegisterDialog.SPECIES }) { Text(stringResource(a.titleRes())) }
                } else {
                    OutlinedButton(onClick = { send(CheckInDraft(activity = a)) }) { Text(stringResource(a.titleRes())) }
                }
            }
        }
        Button(
            onClick = { send(CheckInDraft(notWorking = true)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CarpTheme.colors.warnStrong),
        ) { Text(stringResource(R.string.guided_not_working)) }
        Caption(stringResource(R.string.guided_not_working_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = { dialog = RegisterDialog.REBAIT }) { Text(stringResource(R.string.guided_rebait)) }
            TextButton(onClick = { dialog = RegisterDialog.CHANGE }) { Text(stringResource(R.string.guided_changed)) }
        }
    }
    when (dialog) {
        RegisterDialog.SPECIES -> ChoiceDialog(
            title = stringResource(R.string.guided_catch_species),
            options = Species.entries.map { it to stringResource(it.titleRes()) },
            onPick = { send(CheckInDraft(activity = HookActivity.CATCH, species = it)) },
            onDismiss = { dialog = null },
        )
        RegisterDialog.REBAIT -> ChoiceDialog(
            title = stringResource(R.string.guided_rebait_title),
            options = GroundbaitLevel.entries.map { it to stringResource(it.titleRes()) },
            onPick = { send(CheckInDraft(rebait = it)) },
            onDismiss = { dialog = null },
        )
        RegisterDialog.CHANGE -> ChoiceDialog(
            title = stringResource(R.string.guided_changed_title),
            options = ChangedVariable.entries.map { it to stringResource(it.titleRes()) },
            onPick = { send(CheckInDraft(change = it)) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private enum class RegisterDialog { SPECIES, REBAIT, CHANGE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.forEach { (value, label) ->
                    FilterChip(selected = false, onClick = {
                        onPick(value)
                        onDismiss()
                    }, label = { Text(label) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HistoryCard(rods: List<RodUi>, phase: FishingPhase) {
    val res = LocalContext.current.resources
    CarpCard {
        Text(stringResource(R.string.guided_history_title), style = MaterialTheme.typography.titleMedium)
        rods.forEach { rod ->
            if (rods.size > 1) Text(res.rodLabel(rod.id, rod.name), style = MaterialTheme.typography.titleSmall)
            rod.log.segments.forEach { s ->
                val what = listOfNotNull(res.baitText(s.bait, s.baitName), s.column?.let { res.getString(it.titleRes()) }).joinToString(" · ").ifBlank { res.stepShort(s.kind) }
                Text(stringResource(R.string.guided_history_segment, Formatting.clock(s.start), Formatting.clock(s.end), what, s.bites, s.catches))
            }
            rod.log.proposals.filter { it.decision != Decision.PENDING }.forEach { r ->
                Caption(stringResource(R.string.guided_history_proposal, stringResource(r.decision.titleRes()), Formatting.clock(r.decidedAt), res.proposalText(r.proposal, phase)))
                r.reason?.let { reason ->
                    Caption(stringResource(R.string.guided_history_reason, listOf(stringResource(reason.titleRes()), r.comment).filter { it.isNotBlank() }.joinToString(": ")))
                }
            }
        }
    }
}

@Composable
private fun WarningsCard() {
    CarpCard(border = CardBorder.Dashed) {
        SectionTitle(stringResource(R.string.guided_warnings_title))
        listOf(
            Evidence.PURPLE to R.string.guided_warning_times,
            Evidence.RED to R.string.guided_warning_bathymetry,
            Evidence.RED to R.string.guided_warning_no_data,
            Evidence.REGULATION to R.string.guided_warning_regulation,
            Evidence.GREEN to R.string.guided_warning_no_guarantee,
        ).forEach { (evidence, text) ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AnyEvidenceBadge(evidence)
                Caption(stringResource(text), modifier = Modifier.weight(1f))
            }
        }
    }
}
