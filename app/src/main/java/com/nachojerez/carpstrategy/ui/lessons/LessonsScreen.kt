package com.nachojerez.carpstrategy.ui.lessons

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonSource
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.domain.advice.Lessons
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.conditionsText
import com.nachojerez.carpstrategy.ui.guided.titleRes
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Instant

/**
 * «Lo aprendido»: lecciones de tus sesiones. Las de la IA llegan propuestas y tú decides; las
 * aceptadas se usan en «Qué hago ahora» y en la IA. Se exportan en Markdown para incorporarlas a
 * CONOCIMIENTO.md.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonsScreen(onBack: () -> Unit, viewModel: LessonsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = context.resources
    var adding by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(stringResource(R.string.lessons_title), stringResource(R.string.lessons_overline))
        }
        item {
            CarpCard {
                Text(stringResource(R.string.lessons_intro))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (state.aiConfigured) {
                        Button(onClick = viewModel::reviewLast, enabled = state.review != ReviewState.Running) {
                            Text(stringResource(if (state.review == ReviewState.Running) R.string.lessons_reviewing else R.string.lessons_review_last))
                        }
                    }
                    OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.lessons_add)) }
                    OutlinedButton(onClick = {
                        val md = Lessons.toMarkdown(state.accepted, Formatting.MADRID, Instant.now(), { res.getString(it.titleRes()) }, { res.conditionsText(it) })
                        val send = Intent(Intent.ACTION_SEND).setType("text/markdown")
                            .putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.lessons_export_subject))
                            .putExtra(Intent.EXTRA_TEXT, md)
                        context.startActivity(Intent.createChooser(send, res.getString(R.string.lessons_export)))
                    }, enabled = state.accepted.isNotEmpty()) { Text(stringResource(R.string.lessons_export)) }
                }
                when (val r = state.review) {
                    is ReviewState.Done -> Caption(res.getQuantityString(R.plurals.lessons_review_done, r.added, r.added))
                    ReviewState.Failed -> Caption(stringResource(R.string.lessons_review_failed))
                    ReviewState.NoSession -> Caption(stringResource(R.string.lessons_review_none))
                    else -> Unit
                }
                if (!state.aiConfigured) Caption(stringResource(R.string.lessons_ai_off))
            }
        }
        if (state.proposed.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.lessons_proposed), subtitle = stringResource(R.string.lessons_proposed_subtitle)) }
            items(state.proposed, key = { it.id }) { lesson ->
                LessonCard(lesson) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { viewModel.accept(lesson.id) }) { Text(stringResource(R.string.lessons_accept)) }
                        OutlinedButton(onClick = { viewModel.reject(lesson.id) }) { Text(stringResource(R.string.lessons_reject)) }
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.lessons_accepted), subtitle = stringResource(R.string.lessons_accepted_subtitle)) }
        if (state.accepted.isEmpty()) item { Caption(stringResource(R.string.lessons_none)) }
        items(state.accepted, key = { it.id }) { lesson ->
            LessonCard(lesson) {
                TextButton(onClick = { viewModel.delete(lesson.id) }) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }

    if (adding) AddLessonDialog(onAdd = { topic, text, phase -> viewModel.add(topic, text, phase); adding = false }, onDismiss = { adding = false })
}

@Composable
private fun LessonCard(lesson: Lesson, actions: @Composable () -> Unit) {
    val res = LocalContext.current.resources
    CarpCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(lesson.topic.titleRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            AnyEvidenceBadge(lesson.evidence)
        }
        Text(lesson.text, style = MaterialTheme.typography.bodyLarge)
        res.conditionsText(lesson.conditions).takeIf { it.isNotBlank() }?.let { Caption(it) }
        val origin = stringResource(if (lesson.source == LessonSource.AI) R.string.lessons_source_ai else R.string.lessons_source_user)
        val ref = lesson.knowledgeRef?.let { stringResource(if (lesson.correction) R.string.lessons_corrects else R.string.lessons_confirms, it) }
        Caption(listOfNotNull(origin, Formatting.hour(lesson.createdAt), ref).joinToString(" · "))
        actions()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddLessonDialog(onAdd: (LessonTopic, String, FishingPhase?) -> Unit, onDismiss: () -> Unit) {
    var topic by rememberSaveable { mutableStateOf(LessonTopic.BAIT) }
    var phase by rememberSaveable { mutableStateOf<FishingPhase?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lessons_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(Lessons.MAX_TEXT) }, label = { Text(stringResource(R.string.lessons_text)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.lessons_topic), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LessonTopic.entries.forEach { t -> FilterChip(selected = topic == t, onClick = { topic = t }, label = { Text(stringResource(t.titleRes())) }) }
                }
                Text(stringResource(R.string.lessons_phase), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = phase == null, onClick = { phase = null }, label = { Text(stringResource(R.string.lessons_phase_any)) })
                    FishingPhase.entries.forEach { p -> FilterChip(selected = phase == p, onClick = { phase = p }, label = { Text(stringResource(p.titleRes())) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(topic, text, phase) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
