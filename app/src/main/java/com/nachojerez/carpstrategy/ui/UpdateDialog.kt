package com.nachojerez.carpstrategy.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.update.AvailableUpdate

/**
 * Hay una versión nueva en GitHub: se descarga en el navegador y se instala encima (misma firma,
 * los datos no se borran). Si hay cuenta de Google, además ya están en Drive.
 */
@Composable
fun UpdateDialog(update: AvailableUpdate, syncEnabled: Boolean, onOpened: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_title, update.versionName)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (update.notes.isNotBlank()) Text(update.notes, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (syncEnabled) R.string.update_data_safe_drive else R.string.update_data_safe_local),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, update.downloadUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    // Sin navegador: el usuario puede descargarla desde GitHub más tarde.
                }
                onOpened()
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) } },
    )
}
