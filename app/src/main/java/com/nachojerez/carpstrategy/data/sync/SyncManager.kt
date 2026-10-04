package com.nachojerez.carpstrategy.data.sync

import com.nachojerez.carpstrategy.BuildConfig
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.di.IoDispatcher
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SyncStatus { OFF, SYNCING, SYNCED, PENDING_OFFLINE, NEEDS_SIGN_IN, ERROR }

data class SyncState(
    val enabled: Boolean = false,
    val email: String? = null,
    val lastSync: Instant? = null,
    val status: SyncStatus = SyncStatus.OFF,
    /** Código del último error (HTTP de Drive o de Google Play Services), para diagnosticar. */
    val errorCode: Int? = null,
)

/**
 * Sincroniza los datos del usuario con su Google Drive. La base de datos local sigue siendo la
 * que usa la app (funciona sin cobertura en el embalse); Drive es la copia de referencia: al
 * abrir la app se baja lo último y cada cambio se sube a los pocos segundos si hay conexión.
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    private val store: SyncStore,
    private val drive: DriveClient,
    private val auth: GoogleDriveAuth,
    private val local: LocalSettings,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state
    private var started = false

    /** Al arrancar la app: carga el estado, sincroniza y vigila los cambios. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val enabled = local.get(KEY_ENABLED) == "1"
            _state.value = SyncState(
                enabled = enabled,
                email = local.get(KEY_EMAIL),
                lastSync = local.get(KEY_LAST)?.let { runCatching { Instant.parse(it) }.getOrNull() },
                status = if (enabled) SyncStatus.SYNCED else SyncStatus.OFF,
            )
            if (enabled) sync()
        }
        scope.launch {
            store.changes().drop(1).debounce(CHANGE_DEBOUNCE_MS).collect { if (_state.value.enabled) sync() }
        }
    }

    fun syncNow() {
        scope.launch { sync() }
    }

    /** Tras iniciar sesión: guarda la cuenta y hace la primera sincronización (une si ya hay datos). */
    suspend fun connect(token: String) = withContext(io) {
        val email = runCatching { drive.accountEmail(token) }.getOrNull()
        local.put(KEY_ENABLED, "1")
        local.put(KEY_EMAIL, email)
        _state.update { it.copy(enabled = true, email = email, status = SyncStatus.SYNCING, errorCode = null) }
        sync(token)
    }

    /** Deja de sincronizar. Los datos siguen en el móvil y en Drive: no se borra nada. */
    suspend fun disconnect() = withContext(io) {
        local.put(KEY_ENABLED, null)
        local.put(KEY_HASH, null)
        _state.value = SyncState()
    }

    fun authFailed(code: Int?) {
        _state.update { it.copy(status = SyncStatus.ERROR, errorCode = code) }
    }

    /** Siempre fuera del hilo principal: Drive se llama con OkHttp bloqueante. */
    private suspend fun sync(token: String? = null): Unit = withContext(io) { syncLocked(token) }

    private suspend fun syncLocked(token: String?): Unit = mutex.withLock {
        if (local.get(KEY_ENABLED) != "1") return@withLock
        _state.update { it.copy(status = SyncStatus.SYNCING) }
        val accessToken = token ?: when (val step = auth.authorize()) {
            is AuthStep.Token -> step.value
            is AuthStep.NeedsUser -> {
                _state.update { it.copy(status = SyncStatus.NEEDS_SIGN_IN) }
                return@withLock
            }
            is AuthStep.Failed -> {
                _state.update { it.copy(status = SyncStatus.ERROR, errorCode = step.code) }
                return@withLock
            }
        }
        try {
            run(accessToken)
            val now = clock.instant()
            local.put(KEY_LAST, now.toString())
            _state.update { it.copy(status = SyncStatus.SYNCED, lastSync = now, errorCode = null) }
        } catch (_: DriveException.Offline) {
            _state.update { it.copy(status = SyncStatus.PENDING_OFFLINE) }
        } catch (_: DriveException.Unauthorized) {
            _state.update { it.copy(status = SyncStatus.NEEDS_SIGN_IN) }
        } catch (e: DriveException.Failed) {
            _state.update { it.copy(status = SyncStatus.ERROR, errorCode = e.code) }
        } catch (_: InvalidRemoteFile) {
            _state.update { it.copy(status = SyncStatus.ERROR, errorCode = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nada de la sincronización debe cerrar la app: se muestra como error y se reintenta.
            _state.update { it.copy(status = SyncStatus.ERROR, errorCode = null) }
        }
    }

    private class InvalidRemoteFile : Exception()

    private suspend fun run(token: String) {
        val snapshot = store.snapshot()
        val localHash = snapshot.hash()
        val remote = drive.find(token)
        // Un archivo sin huella (subido a mano o a medias) se baja para calcularla.
        var remoteSnapshot: SyncSnapshot? = null
        val remoteHash = remote?.let { file ->
            file.hash ?: downloadSnapshot(token, file.id).also { remoteSnapshot = it }.hash()
        }
        when (decideSync(localHash, snapshot.isEmpty, local.get(KEY_HASH), remoteHash)) {
            SyncAction.NONE -> remoteHash?.let { local.put(KEY_HASH, it) }
            SyncAction.UPLOAD -> upload(token, remote?.id, snapshot)
            SyncAction.DOWNLOAD -> {
                val incoming = remoteSnapshot ?: downloadSnapshot(token, remote!!.id)
                store.replaceWith(incoming)
                local.put(KEY_HASH, store.snapshot().hash())
            }
            SyncAction.MERGE -> {
                val incoming = remoteSnapshot ?: downloadSnapshot(token, remote!!.id)
                val merged = mergeSnapshots(preferred = snapshot, other = incoming)
                store.replaceWith(merged)
                upload(token, remote?.id, store.snapshot())
            }
        }
    }

    private fun downloadSnapshot(token: String, id: String): SyncSnapshot =
        SyncJson.decode(drive.download(token, id)) ?: throw InvalidRemoteFile()

    private suspend fun upload(token: String, existingId: String?, snapshot: SyncSnapshot) {
        val hash = snapshot.hash()
        drive.upload(token, existingId, SyncJson.encode(snapshot, clock.instant(), BuildConfig.VERSION_NAME), hash)
        local.put(KEY_HASH, hash)
    }

    companion object {
        const val KEY_ENABLED = "local_sync_enabled"
        const val KEY_EMAIL = "local_sync_email"
        const val KEY_HASH = "local_sync_hash"
        const val KEY_LAST = "local_sync_last"
        private const val CHANGE_DEBOUNCE_MS = 15_000L
    }
}
