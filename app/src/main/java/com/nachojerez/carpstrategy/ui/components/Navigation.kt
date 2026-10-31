package com.nachojerez.carpstrategy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.theme.Spacing

/**
 * Flecha «Volver» de las subpantallas (revisión de UI, fase 7.28): la misma en todas, arriba a la
 * izquierda y con su texto para lectores de pantalla. Sustituye al botón de texto «Volver».
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier.offset(x = (-12).dp)) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
    }
}

/**
 * Fila que abre otra pantalla: título, una línea de qué hay dentro y «›». Toda la fila se puede
 * tocar (≥ 48 dp). Para listas de accesos dentro de una [CarpCard].
 */
@Composable
fun NavRow(title: String, subtitle: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            subtitle?.let { Caption(it) }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Título de un paso numerado («① Embalse»), común a Preparar y Estrategia. */
@Composable
fun StepHeading(number: Int, title: String, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.strategy_step, number, title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}
