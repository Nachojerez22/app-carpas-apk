package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.data.local.cacheKey
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Fulfilled
import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val sessions: SessionDao,
    private val predictions: PredictionSnapshotDao,
) : JournalRepository {

    override fun observeSessions(): Flow<List<Session>> =
        sessions.observeAll().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun save(session: Session): Long {
        val id = sessions.upsert(session.toEntity())
        // Upsert devuelve -1 cuando actualiza una fila existente.
        return if (id == -1L) session.id else id
    }

    override suspend fun delete(id: Long) = sessions.delete(id)

    override suspend fun restore(sessions: List<Session>): Int {
        val existing = this.sessions.observeAll().first().map { it.toDomain() }
        val added = JournalJson.newSessions(existing, sessions)
        added.forEach { this.sessions.upsert(it.copy(id = 0).toEntity()) }
        return added.size
    }

    override suspend fun recordPrediction(snapshot: PredictionSnapshot) {
        val second = snapshot.computedAt.epochSecond
        predictions.insertOrReplace(
            PredictionSnapshotEntity(
                computedAtEpochSecond = second,
                hourKey = second / 3600,
                locationKey = snapshot.location.cacheKey(),
                json = JournalJson.encodePrediction(snapshot),
            ),
        )
        predictions.deleteOlderThan(snapshot.computedAt.minus(SNAPSHOT_RETENTION).epochSecond)
    }

    override suspend fun predictionsBetween(from: Instant, to: Instant): List<PredictionSnapshot> =
        predictions.between(from.epochSecond, to.epochSecond).mapNotNull { JournalJson.decodePrediction(it.json) }

    companion object {
        /** Las valoraciones solo sirven para sesiones de las 24 h siguientes; se guarda margen. */
        val SNAPSHOT_RETENTION: Duration = Duration.ofDays(30)

        internal fun Session.toEntity() = SessionEntity(
            id = id,
            startEpochSecond = start.epochSecond,
            endEpochSecond = end?.epochSecond,
            latitude = location.latitude,
            longitude = location.longitude,
            zone = zone?.name,
            zoneDetail = zoneDetail,
            depthM = depthM,
            rods = rods,
            rodHoursOverride = rodHoursOverride,
            bait = bait,
            rig = rig,
            groundbaitKg = groundbaitKg,
            otherAnglers = otherAnglers,
            bites = bites,
            losses = losses,
            catchesJson = JournalJson.encodeCatches(catches),
            blank = blank,
            predictionJson = prediction?.let(JournalJson::encodePrediction),
            contextJson = context?.let(JournalJson::encodeContext),
            fulfilled = fulfilled?.name,
            notes = notes,
            createdAtEpochMs = createdAt.toEpochMilli(),
        )

        internal fun SessionEntity.toDomain() = Session(
            id = id,
            start = Instant.ofEpochSecond(startEpochSecond),
            end = endEpochSecond?.let(Instant::ofEpochSecond),
            location = GeoPoint(latitude, longitude),
            zone = FishingZone.entries.firstOrNull { it.name == zone },
            zoneDetail = zoneDetail,
            depthM = depthM,
            rods = rods,
            rodHoursOverride = rodHoursOverride,
            bait = bait,
            rig = rig,
            groundbaitKg = groundbaitKg,
            otherAnglers = otherAnglers,
            bites = bites,
            losses = losses,
            catches = JournalJson.decodeCatches(catchesJson),
            blank = blank,
            prediction = JournalJson.decodePrediction(predictionJson),
            context = JournalJson.decodeContext(contextJson),
            fulfilled = Fulfilled.entries.firstOrNull { it.name == fulfilled },
            notes = notes,
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        )
    }
}
