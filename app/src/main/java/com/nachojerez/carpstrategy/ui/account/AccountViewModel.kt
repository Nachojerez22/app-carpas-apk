package com.nachojerez.carpstrategy.ui.account

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.data.sync.AuthStep
import com.nachojerez.carpstrategy.data.sync.GoogleDriveAuth
import com.nachojerez.carpstrategy.data.sync.SyncManager
import com.nachojerez.carpstrategy.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Cuenta de Google para guardar los datos en Drive (Lugar → Cuenta). */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sync: SyncManager,
    private val auth: GoogleDriveAuth,
) : ViewModel() {
    val state: StateFlow<SyncState> = sync.state

    private val pending = MutableStateFlow<PendingIntent?>(null)

    /** Pantalla de Google que hay que abrir (elegir cuenta y aceptar el permiso). */
    val authRequest: StateFlow<PendingIntent?> = pending

    private val busy = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = busy

    fun signIn() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            when (val step = auth.authorize()) {
                is AuthStep.Token -> connect(step.value)
                is AuthStep.NeedsUser -> pending.value = step.intent
                is AuthStep.Failed -> {
                    sync.authFailed(step.code)
                    busy.value = false
                }
            }
        }
    }

    fun authRequestLaunched() {
        pending.value = null
    }

    /** Vuelta de la pantalla de Google. */
    fun onAuthResult(data: Intent?) {
        val token = auth.tokenFromResult(data)
        if (token == null) {
            busy.value = false
            return
        }
        viewModelScope.launch { connect(token) }
    }

    private suspend fun connect(token: String) {
        try {
            sync.connect(token)
        } finally {
            busy.value = false
        }
    }

    fun syncNow() = sync.syncNow()

    fun signOut() {
        viewModelScope.launch { sync.disconnect() }
    }
}
