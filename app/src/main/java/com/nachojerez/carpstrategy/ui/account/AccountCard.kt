package com.nachojerez.carpstrategy.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.sync.SyncState
import com.nachojerez.carpstrategy.data.sync.SyncStatus
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CardBorder
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.format.DateTimeFormatter

private val SYNC_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Formatting.SPANISH)

/** Tarjeta «Cuenta de Google»: iniciar sesión una vez y los datos quedan en tu Drive. */
@Composable
fun AccountCard(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val request by viewModel.authRequest.collectAsStateWithLifecycle()
    val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onAuthResult(result.data)
    }
    LaunchedEffect(request) {
        request?.let {
            launcher.launch(IntentSenderRequest.Builder(it.intentSender).build())
            viewModel.authRequestLaunched()
        }
    }

    CarpCard(border = if (state.status == SyncStatus.NEEDS_SIGN_IN || state.status == SyncStatus.ERROR) CardBorder.Warning else CardBorder.Normal) {
        if (!state.enabled) {
            Text(stringResource(R.string.account_intro))
            Caption(stringResource(R.string.account_privacy))
            statusText(state)?.let { Caption(it) }
            Button(onClick = viewModel::signIn, enabled = !signingIn, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (signingIn) R.string.account_signing_in else R.string.account_sign_in))
            }
        } else {
            Text(
                state.email?.let { stringResource(R.string.account_connected_as, it) } ?: stringResource(R.string.account_connected),
                style = MaterialTheme.typography.titleSmall,
            )
            statusText(state)?.let { Text(it) }
            Caption(stringResource(R.string.account_how))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.status == SyncStatus.NEEDS_SIGN_IN) {
                    Button(onClick = viewModel::signIn, enabled = !signingIn) { Text(stringResource(R.string.account_sign_in_again)) }
                } else {
                    Button(onClick = viewModel::syncNow, enabled = state.status != SyncStatus.SYNCING) { Text(stringResource(R.string.account_sync_now)) }
                }
                OutlinedButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.account_sign_out)) }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            text = { Text(stringResource(R.string.account_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.account_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun statusText(state: SyncState): String? = when (state.status) {
    SyncStatus.OFF -> null
    SyncStatus.SYNCING -> stringResource(R.string.account_syncing)
    SyncStatus.SYNCED -> state.lastSync?.let { stringResource(R.string.account_synced_at, SYNC_TIME.format(it.atZone(Formatting.MADRID))) }
    SyncStatus.PENDING_OFFLINE -> stringResource(R.string.account_offline)
    SyncStatus.NEEDS_SIGN_IN -> stringResource(R.string.account_needs_sign_in)
    SyncStatus.ERROR -> when (state.errorCode) {
        null -> stringResource(R.string.account_error)
        DEVELOPER_ERROR -> stringResource(R.string.account_error_config)
        else -> stringResource(R.string.account_error_code, state.errorCode)
    }
}

/** Código de Google Play Services cuando la credencial de Google Cloud no coincide (paquete o SHA-1). */
private const val DEVELOPER_ERROR = 10
