package com.intentevolved.com.intentevolved.voluntas

/**
 * Constants and helpers for the Voluntas ID space.
 *
 * Entity IDs have the high bit clear (0x0000000000000000 - 0x7FFFFFFFFFFFFFFF).
 * Literal IDs have the high bit set  (0x8000000000000000 - 0xFFFFFFFFFFFFFFFF).
 */
object VoluntasIds {

    // --- Bootstrap entity IDs (relationship types) ---
    const val DEFINES_TYPE: Long  = 1L
    const val DEFINES_FIELD: Long = 2L
    const val INSTANTIATES: Long  = 3L
    const val SETS_FIELD: Long    = 4L
    const val ADDS_PARTICIPANT: Long = 5L
    // 6 = depends_on (unused for now)

    /** The "string intent" type entity, auto-bootstrapped. */
    const val STRING_INTENT_TYPE: Long = 7L

    /** The root intent always has entity ID 0. */
    const val ROOT_INTENT: Long = 0L

    /** First user-allocatable entity ID (after bootstrap).
     *  ID
     *  s below this are reserved.
     *  */
    const val FIRST_USER_ENTITY: Long = 1000L

    // --- Literal bit ---
    const val LITERAL_BIT: Long = Long.MIN_VALUE // 0x8000000000000000

    fun isLiteral(id: Long): Boolean = (id and LITERAL_BIT) != 0L
    fun isEntity(id: Long): Boolean  = (id and LITERAL_BIT) == 0L

    /** Strip the literal bit to get the ordinal. */
    fun literalOrdinal(id: Long): Long = id and LITERAL_BIT.inv()

    /** Create a literal ID from an ordinal. */
    fun literalId(ordinal: Long): Long = LITERAL_BIT or ordinal
}
