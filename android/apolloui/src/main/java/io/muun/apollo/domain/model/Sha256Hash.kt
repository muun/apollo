package io.muun.apollo.domain.model

import io.muun.common.utils.Encodings

/**
 * This represents a submarineSwap's/ lightning network payment's preimage. The protocol specifies
 * it to be a 32-byte array, which is exactly what we our Sha256Hash class represents. So, we do
 * this fugly alias in order to save us some code repetition.
 */
typealias Preimage = Sha256Hash

class Sha256Hash private constructor(private val hex: String) {

    companion object {

        private const val LENGTH = 32 // bytes

        fun fromBytes(bytes: ByteArray): Sha256Hash {
            check(bytes.size == LENGTH)
            return Sha256Hash(Encodings.bytesToHex(bytes))
        }

        fun fromHex(hex: String): Sha256Hash {
            check(Encodings.hexToBytes(hex).size == LENGTH)
            return Sha256Hash(hex)
        }
    }

    fun toBytes() =
        Encodings.hexToBytes(hex)

    /**
     * Return hex encoding representation of this hash.
     */
    override fun toString() =
        hex

    override fun hashCode(): Int =
        hex.hashCode()

    override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as Sha256Hash

        return hex == other.hex
    }
}