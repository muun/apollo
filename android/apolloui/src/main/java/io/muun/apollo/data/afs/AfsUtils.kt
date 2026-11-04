package io.muun.apollo.data.afs

import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import java.security.MessageDigest

object AfsUtils {
    /**
     * Normalizes the given epoch time to UTC midnight, reducing it to day-level granularity.
     *
     * The returned value represents the epoch at 00:00:00.000 UTC of the same day
     * as the input timestamp.
     */
    @JvmStatic
    fun epochAtUtcMidnight(epochMillis: Long): Long {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Computes the SHA-256 digest of the given UTF-8 string and returns it as a
     * lowercase hexadecimal string.
     */
    @JvmStatic
    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}