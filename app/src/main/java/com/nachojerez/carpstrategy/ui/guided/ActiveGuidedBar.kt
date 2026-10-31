package com.nachojerez.carpstrategy.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing

/**
 * Banda «Sesión guiada en curso · desde las 07:30 · próximo aviso 08:00» encima de la barra de
 * pestañas. Toda la banda abre la sesión guiada.
 */
@Composable
fun ActiveGuidedBar(active: ActiveGuided, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.primaryContainer)
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = cs.onPrimaryContainer)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.guided_bar_title), style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
            val since = stringResource(R.string.guided_bar_since, Formatting.clock(active.start))
            val line = active.nextCheckIn?.let { since + " · " + stringResource(R.string.guided_bar_next, Formatting.clock(it)) } ?: since
            Text(line, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
        }
        Text(stringResource(R.string.guided_bar_open), style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = cs.onPrimaryContainer)
    }
}
