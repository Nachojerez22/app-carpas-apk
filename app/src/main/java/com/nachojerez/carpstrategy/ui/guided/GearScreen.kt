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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.theme.Spacing

/** «Mi equipo»: cebos y montajes que lleva el usuario; las propuestas eligen entre ellos. */
@Composable
fun GearScreen(onBack: () -> Unit, viewModel: GearViewModel = hiltViewModel()) {
    val gear by viewModel.gear.collectAsStateWithLifecycle()
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
        GearCategory.entries.forEach { category ->
            item {
                SectionTitle(stringResource(if (category == GearCategory.BAIT) R.string.gear_baits else R.string.gear_rigs))
                GearList(gear.filter { it.category == category }, onEdit = { draft = GearDraft.of(it) })
                OutlinedButton(onClick = { draft = GearDraft(category = category) }, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(if (category == GearCategory.BAIT) R.string.gear_add_bait else R.string.gear_add_rig))
                }
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
        )
    }
}

@Composable
private fun GearList(items: List<GearItem>, onEdit: (GearItem) -> Unit) {
    CarpCard {
        if (items.isEmpty()) Caption(stringResource(R.string.gear_none))
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(item) }.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.name, modifier = Modifier.weight(1f))
                val type = item.baitType?.titleRes() ?: item.rigType?.titleRes()
                type?.let { Caption(stringResource(it)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearDialog(draft: GearDraft, onChange: (GearDraft) -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.category == GearCategory.BAIT) R.string.gear_add_bait else R.string.gear_add_rig)) },
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
                Text(stringResource(R.string.gear_type), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (draft.category == GearCategory.BAIT) {
                        BaitType.entries.forEach { t ->
                            FilterChip(selected = draft.baitType == t, onClick = { onChange(draft.copy(baitType = t)) }, label = { Text(stringResource(t.titleRes())) })
                        }
                    } else {
                        RigType.entries.forEach { t ->
                            FilterChip(selected = draft.rigType == t, onClick = { onChange(draft.copy(rigType = t)) }, label = { Text(stringResource(t.titleRes())) })
                        }
                    }
                }
                if (draft.name.isBlank()) Caption(stringResource(R.string.gear_name_required))
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
