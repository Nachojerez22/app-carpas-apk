package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.data.userdata.JournalRepositoryImpl.Companion.toDomain
import com.nachojerez.carpstrategy.data.userdata.JournalRepositoryImpl.Companion.toEntity
import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Fulfilled
import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionContext
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JournalJsonTest {
    private val point = GeoPoint(38.35, -6.70)
    private val start = Instant.parse("2026-09-27T05:20:00Z")

    private val session = Session(
        id = 7,
        start = start,
        end = Instant.parse("2026-09-27T10:40:00Z"),
        location = point,
        zone = FishingZone.NORTH,
        zoneDetail = "carrizal norte",
        depthM = 2.5,
        rods = 3,
        bait = "Boilie de pescado 20 mm",
        rig = "Pelo · plomo en línea",
        groundbaitKg = 1.5,
        otherAnglers = 1,
        bites = 4,
        losses = 1,
        catches = listOf(Catch(Instant.parse("2026-09-27T06:05:00Z"), 8.4, 2, "Carpa común"), Catch(weightKg = 6.1)),
        blank = false,
        prediction = PredictionSnapshot(
            Instant.parse("2026-09-26T19:40:00Z"), point, false, 0.72, FavorabilityBand.FAVORABLE, RuleLevel.PHYSICAL, FeedingDemand.MEDIUM,
        ),
        context = SessionContext(waterTempC = 18.1, waterMeasured = true, moonIllumination = 0.3),
        fulfilled = Fulfilled.PARTLY,
        notes = "Burbujeo en el borde del carrizo",
        createdAt = Instant.parse("2026-09-27T11:00:00Z"),
    )

    @Test
    fun `la sesion sobrevive a la entidad de Room`() {
        assertEquals(session, session.toEntity().toDomain())
        val ongoing = session.copy(end = null, catches = emptyList(), blank = null, prediction = null, context = null, fulfilled = null, zone = null)
        assertEquals(ongoing, ongoing.toEntity().toDomain())
    }

    @Test
    fun `exportar e importar el diario conserva las sesiones`() {
        val text = JournalJson.export(listOf(session), Instant.parse("2026-10-03T10:00:00Z"))
        assertTrue(text.contains("\"formato\": \"carpstrategy-diario\""))
        assertTrue(text.contains("\"horas_cana\": 16.0"))
        assertEquals(listOf(session.copy(id = 0)), JournalJson.parseExport(text))
    }

    @Test
    fun `json corrupto no rompe la lectura`() {
        assertEquals(emptyList<Catch>(), JournalJson.decodeCatches("no es json"))
        assertNull(JournalJson.decodePrediction("{}"))
        assertNull(JournalJson.parseExport("""{"formato":"otro","version":1,"exportado":"x","sesiones":[]}"""))
    }
}
