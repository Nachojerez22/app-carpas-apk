package com.nachojerez.carpstrategy.domain.manual

/** Fuentes de datos que se pueden combinar. */
enum class DataSource { MANUAL, AEMET, MODELS }

/**
 * Orden de preferencia elegido por el usuario. Solo contiene las fuentes activas; para cada hora
 * y variable se usa la primera fuente de la lista que tenga valor.
 */
data class SourcePriority(val order: List<DataSource>) {
    init {
        require(order.isNotEmpty()) { "Debe haber al menos una fuente activa" }
        require(order.distinct().size == order.size) { "Fuentes repetidas: $order" }
    }

    fun isEnabled(source: DataSource) = source in order

    fun moveUp(source: DataSource): SourcePriority {
        val i = order.indexOf(source)
        if (i <= 0) return this
        return SourcePriority(order.toMutableList().apply { add(i - 1, removeAt(i)) })
    }

    fun moveDown(source: DataSource): SourcePriority {
        val i = order.indexOf(source)
        if (i < 0 || i == order.lastIndex) return this
        return SourcePriority(order.toMutableList().apply { add(i + 1, removeAt(i)) })
    }

    /** Activa (al final) o desactiva una fuente. Nunca deja la lista vacía. */
    fun toggle(source: DataSource): SourcePriority = when {
        source !in order -> SourcePriority(order + source)
        order.size == 1 -> this
        else -> SourcePriority(order - source)
    }

    /** Serialización estable para guardarla ("MANUAL,AEMET,MODELS"). */
    fun encode(): String = order.joinToString(",") { it.name }

    companion object {
        val DEFAULT = SourcePriority(listOf(DataSource.MANUAL, DataSource.AEMET, DataSource.MODELS))

        /** Tolerante: ignora nombres desconocidos o repetidos; si queda vacía, usa [DEFAULT]. */
        fun decode(text: String?): SourcePriority {
            val sources = text.orEmpty().split(',')
                .mapNotNull { name -> DataSource.entries.firstOrNull { it.name == name.trim() } }
                .distinct()
            return if (sources.isEmpty()) DEFAULT else SourcePriority(sources)
        }
    }
}
