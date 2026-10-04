package com.nachojerez.carpstrategy.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Resultado de pedir permiso a Google para la carpeta privada de la app en Drive. */
sealed interface AuthStep {
    data class Token(val value: String) : AuthStep

    /** Google necesita que el usuario elija cuenta o acepte: se abre su pantalla. */
    data class NeedsUser(val intent: PendingIntent) : AuthStep

    /** Error de Google (sin servicios de Google, credencial mal configurada…). [code] de ApiException. */
    data class Failed(val code: Int?) : AuthStep
}

/**
 * Inicio de sesión con Google solo para Drive (`drive.appdata`) mediante la API de autorización
 * de Google Play Services. No guarda contraseñas ni tokens: Google los gestiona y la sesión queda
 * abierta hasta que el usuario la cierre o retire el permiso en su cuenta.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DriveClient.SCOPE)))
        .build()

    suspend fun authorize(): AuthStep = try {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pending = result.pendingIntent
        val token = result.accessToken
        when {
            result.hasResolution() && pending != null -> AuthStep.NeedsUser(pending)
            token != null -> AuthStep.Token(token)
            else -> AuthStep.Failed(null)
        }
    } catch (e: ApiException) {
        AuthStep.Failed(e.statusCode)
    }

    /** Token tras la pantalla de Google; null si el usuario canceló o hubo error. */
    fun tokenFromResult(data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
}
