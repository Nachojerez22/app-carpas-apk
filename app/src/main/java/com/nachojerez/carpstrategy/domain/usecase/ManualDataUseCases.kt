package com.nachojerez.carpstrategy.domain.usecase

import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecordValidator
import com.nachojerez.carpstrategy.domain.manual.RawRecord
import com.nachojerez.carpstrategy.domain.manual.ValidationResult
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.ManualDataRepository
import java.time.Clock
import javax.inject.Inject

/** Valida un registro del formulario y, si no tiene errores, lo guarda. */
class SaveManualRecordUseCase @Inject constructor(
    private val repository: ManualDataRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(raw: RawRecord, location: GeoPoint, existingId: Long = 0): ValidationResult {
        val result = ManualRecordValidator.validate(
            raw = raw,
            recordNumber = null,
            location = location,
            appLocation = location,
            origin = ManualOrigin.Typed,
            now = clock.instant(),
            existingId = existingId,
        )
        result.record?.let { repository.save(it) }
        return result
    }
}
