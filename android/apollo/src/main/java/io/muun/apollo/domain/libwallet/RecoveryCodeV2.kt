package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.libwallet.errors.InvalidRecoveryCodeFormatError
import io.muun.apollo.domain.model.RecoveryCode
import io.muun.common.utils.Preconditions
import libwallet.Libwallet

class RecoveryCodeV2 private constructor(code: String) {

    companion object {

        const val SEPARATOR = '-'

        const val SEGMENT_COUNT = 8
        const val SEGMENT_LENGTH = 4

        /**
         * Create a random RecoveryCode V2.
         */
        @JvmStatic
        fun createRandom(): RecoveryCodeV2 =
            fromString(Libwallet.generateRecoveryCode())

        /**
         * Create a RecoveryCode V2 from a string representation.
         */
        @JvmStatic
        fun fromString(code: String): RecoveryCodeV2 {
            return RecoveryCodeV2(code)
        }
    }

    private val segments: List<String> = code.split(SEPARATOR)

    init {
        validate(code)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as RecoveryCodeV2

        if (segments != other.segments) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        return segments.hashCode()
    }

    override fun toString(): String {
        return segments.joinToString(separator = SEPARATOR.toString())
    }

    /**
     * Extract a segment out of this RecoveryCode.
     */
    fun getSegment(segmentIndex: Int): String {
        Preconditions.checkArgument(segmentIndex >= 0 && segmentIndex < segments.size)
        return segments[segmentIndex]
    }

    private fun validate(code: String) {

        // Detect alphabet errors first (so incomplete codes are already known to be wrong):
        for (segment in segments) {
            validateAlphabet(segment)
        }

        // Detect length errors after (incomplete codes fail here, caller can handle differently):
        if (segments.size != SEGMENT_COUNT) {
            throw RecoveryCode.RecoveryCodeLengthError()
        }

        for (segment in segments) {
            if (segment.length != SEGMENT_LENGTH) {
                throw RecoveryCode.RecoveryCodeLengthError()
            }
        }

        // TODO everything from above should be done by this libwallet call
        // For now, we can only use it to validate fully entered codes

        try {
            Libwallet.validateRecoveryCode(code)
        } catch (e: Exception) {
            throw InvalidRecoveryCodeFormatError(e)
        }
    }

    private fun validateAlphabet(segment: String) {
        val alphabet: MutableSet<Char> = HashSet()

        for (c in Libwallet.RecoveryCodeAlphabet.toCharArray()) {
            alphabet.add(c)
        }

        segment.forEach { c ->
            if (!alphabet.contains(c)) {
                throw RecoveryCode.RecoveryCodeAlphabetError()
            }
        }
    }
}