package com.nachojerez.carpstrategy.domain.repository

import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Diario de sesiones y registro de valoraciones previas. Solo en el móvil. */
interface JournalRepository {
    /** Todas las sesiones, de la más reciente a la más antigua. */
    fun observeSessions(): Flow<List<Session>>

    /** Inserta o, si `id != 0`, sustituye. Devuelve el id. */
    suspend fun save(session: Session): Long

    suspend fun delete(id: Long)

    /** Restaura sesiones de una copia; las que ya existen (mismo inicio) se omiten. Devuelve cuántas añade. */
    suspend fun restore(sessions: List<Session>): Int

    /** Guarda la valoración de la app (una por hora y lugar: la última de la hora sustituye). */
    suspend fun recordPrediction(snapshot: PredictionSnapshot)

    /** Valoraciones calculadas entre [from] y [to]. */
    suspend fun predictionsBetween(from: Instant, to: Instant): List<PredictionSnapshot>
}
