package com.intentevolved.com.intentevolved.voluntas

import voluntas.v1.Literal

/**
 * Content-addressed literal storage.
 *
 * Each unique value gets exactly one literal ID.  The store pre-registers
 * bootstrap literal names (defines_type, defines_field, …) so that their
 * well-known IDs are always consistent.
 */
class LiteralStore {

    /** value → Literal (which contains the ID) */
    private val byValue = mutableMapOf<Any, Literal>()

    /** id → Literal */
    private val byId = mutableMapOf<Long, Literal>()

    /** Next ordinal to allocate (bootstrap reserves 1-6). */
    private var nextOrdinal = 7L

    init {
        // Pre-register bootstrap literal names at well-known ordinals.
        registerBootstrap(1L, "defines_type")
        registerBootstrap(2L, "defines_field")
        registerBootstrap(3L, "instantiates")
        registerBootstrap(4L, "sets_field")
        registerBootstrap(5L, "adds_participant")
        registerBootstrap(6L, "depends_on")
        // Reserve some well-known field name literals
        registerBootstrap(7L, "text")
        registerBootstrap(8L, "parent")
        registerBootstrap(9L, "STRING")
        registerBootstrap(10L, "INTENT_REF")
        nextOrdinal = 11L
    }

    private fun registerBootstrap(ordinal: Long, value: String) {
        val id = VoluntasIds.literalId(ordinal)
        val literal = Literal.newBuilder()
            .setId(id)
            .setStringVal(value)
            .build()
        byValue[value] = literal
        byId[id] = literal
    }

    /**
     * Returns the literal ID for this value, creating a new literal if needed.
     * Supports String, Long (int), Double, Boolean, and ByteArray.
     */
    fun getOrCreate(value: Any): Long {
        // For ByteArray, use a wrapper for content-based equality
        val key = if (value is ByteArray) ByteArrayWrapper(value) else value
        val existing = byValue[key]
        if (existing != null) return existing.id

        val ordinal = nextOrdinal++
        val id = VoluntasIds.literalId(ordinal)
        val builder = Literal.newBuilder().setId(id)
        when (value) {
            is String    -> builder.stringVal = value
            is Long      -> builder.intVal = value
            is Int       -> builder.intVal = value.toLong()
            is Double    -> builder.doubleVal = value
            is Float     -> builder.doubleVal = value.toDouble()
            is Boolean   -> builder.boolVal = value
            is ByteArray -> builder.bytesVal = com.google.protobuf.ByteString.copyFrom(value)
            else -> throw IllegalArgumentException("Unsupported literal type: ${value::class}")
        }
        val literal = builder.build()
        byValue[key] = literal
        byId[id] = literal
        return id
    }

    /** Look up a literal by ID. */
    fun getById(id: Long): Literal? = byId[id]

    /** Get the string value of a literal, or null. */
    fun getString(id: Long): String? {
        val lit = byId[id] ?: return null
        return if (lit.hasStringVal()) lit.stringVal else null
    }

    /** Returns all literals in the store. */
    fun allLiterals(): Collection<Literal> = byId.values

    /** Number of literals in the store. */
    fun size(): Int = byId.size

    /**
     * Register an externally-created literal (e.g. during replay from a Stream).
     * Updates the nextOrdinal if needed to avoid collisions.
     */
    fun register(literal: Literal) {
        val key: Any = when {
            literal.hasStringVal() -> literal.stringVal
            literal.hasIntVal()    -> literal.intVal
            literal.hasDoubleVal() -> literal.doubleVal
            literal.hasBoolVal()   -> literal.boolVal
            literal.hasBytesVal()  -> ByteArrayWrapper(literal.bytesVal.toByteArray())
            else -> throw IllegalArgumentException("Literal has no value set")
        }
        byValue[key] = literal
        byId[literal.id] = literal
        val ordinal = VoluntasIds.literalOrdinal(literal.id)
        if (ordinal >= nextOrdinal) {
            nextOrdinal = ordinal + 1
        }
    }

    /** Wrapper for byte arrays that provides content-based equals/hashCode. */
    private data class ByteArrayWrapper(val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is ByteArrayWrapper && data.contentEquals(other.data)
        override fun hashCode(): Int = data.contentHashCode()
    }
}
