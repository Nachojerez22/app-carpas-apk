package com.nachojerez.carpstrategy.domain.model

/** Causa de un fallo al actualizar datos externos. La app sigue mostrando la última caché. */
enum class DataError {
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
    MISSING_API_KEY,
    UNAUTHORIZED,
    RATE_LIMITED,
    NO_DATA,
}

sealed interface RefreshOutcome {
    data object Success : RefreshOutcome
    data class Failure(val error: DataError, val detail: String? = null) : RefreshOutcome
}
