package com.tkrz.qrtix.utils

import kotlin.random.Random

object TicketFormatters {

    // Characters for token generation, excluded: 0, O, 1, I
    private val SAFE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /**
     * Generates a random token of the specified length using safe characters.
     */
    fun generateRandomToken(length: Int): String {
        return (1..length)
            .map { SAFE_CHARS[Random.nextInt(SAFE_CHARS.length)] }
            .joinToString("")
    }

    /**
     * Formats a ticket code into the standard format:
     * [PREFIX]-[CATEGORY]-[4-DIGIT]-[4-RANDOM]
     *
     * Example: EVNT1-VIP-0001-A2B4
     */
    fun formatTicketCode(
        prefix: String,
        category: String,
        sequenceNumber: Int,
        randomToken: String
    ): String {
        val paddedSequence = sequenceNumber.toString().padStart(4, '0')
        return "${prefix.uppercase()}-${category.uppercase()}-$paddedSequence-${randomToken.uppercase()}"
    }
}
