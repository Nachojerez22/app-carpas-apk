package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.data.local.cacheKey
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

internal object UserDataMappers {
    private const val ORIGIN_TYPED = "typed"
    private const val ORIGIN_IMPORTED = "imported"

    fun RecordPeriod.key(): String = when (this) {
        is RecordPeriod.At -> "t:${time.epochSecond}"
        is RecordPeriod.Day -> "d:$date"
    }

    fun ManualRecord.toEntity(): ManualRecordEntity = ManualRecordEntity(
        id = id,
        periodKey = period.key(),
        epochSecond = (period as? RecordPeriod.At)?.time?.epochSecond,
        date = (period as? RecordPeriod.Day)?.date?.toString(),
        latitude = location.latitude,
        longitude = location.longitude,
        locationKey = location.cacheKey(),
        source = source,
        origin = if (origin is ManualOrigin.Imported) ORIGIN_IMPORTED else ORIGIN_TYPED,
        originFile = (origin as? ManualOrigin.Imported)?.fileName,
        valuesJson = JsonObject(values.entries.associate { (field, value) -> field.key to JsonPrimitive(value) }).toString(),
        notes = notes,
        createdAtEpochMs = createdAt.toEpochMilli(),
    )

    /** Null si la fila está corrupta (no debería ocurrir; se ignora en lugar de romper la lista). */
    fun ManualRecordEntity.toDomain(): ManualRecord? {
        val period = when {
            epochSecond != null -> RecordPeriod.At(Instant.ofEpochSecond(epochSecond))
            date != null -> runCatching { RecordPeriod.Day(LocalDate.parse(date)) }.getOrNull()
            else -> null
        } ?: return null
        val values = runCatching { Json.parseToJsonElement(valuesJson) as JsonObject }.getOrNull()
            ?.mapNotNull { (key, element) ->
                val field = ManualField.fromKey(key) ?: return@mapNotNull null
                val value = (element as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                field to value
            }
            ?.toMap()
            ?: return null
        return ManualRecord(
            id = id,
            period = period,
            location = GeoPoint(latitude, longitude),
            source = source,
            origin = if (origin == ORIGIN_IMPORTED) ManualOrigin.Imported(originFile.orEmpty()) else ManualOrigin.Typed,
            values = values,
            notes = notes,
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        )
    }
}
