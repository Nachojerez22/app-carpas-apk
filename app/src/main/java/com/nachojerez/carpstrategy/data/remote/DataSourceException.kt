package com.nachojerez.carpstrategy.data.remote

import com.nachojerez.carpstrategy.domain.model.DataError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** Fallo de una fuente remota, ya clasificado para el dominio. */
class DataSourceException(
    val error: DataError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.name, cause)

/**
 * Ejecuta [block] traduciendo errores técnicos a [DataSourceException]. Las
 * [DataSourceException] y las cancelaciones se propagan tal cual.
 */
internal suspend fun <T> classifyErrors(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: DataSourceException) {
    throw e
} catch (e: IOException) {
    throw DataSourceException(DataError.NETWORK, e.message, e)
} catch (e: SerializationException) {
    throw DataSourceException(DataError.INVALID_RESPONSE, e.message, e)
} catch (e: IllegalArgumentException) {
    throw DataSourceException(DataError.INVALID_RESPONSE, e.message, e)
} catch (e: IllegalStateException) {
    throw DataSourceException(DataError.INVALID_RESPONSE, e.message, e)
}
