package io.muun.apollo.data.afs

import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset

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
}