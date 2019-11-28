package io.muun.apollo.data.logging

import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit

// We'll use type aliases to help callers avoid mixing up parameters:
typealias IdempotencyKey = String

object LoggingRequestTracker {

    private const val MAX_MEMORY_SIZE = 5
    private val ZDT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    data class Entry(
        val url: String,
        val startedAt: ZonedDateTime,
        var endedAt: ZonedDateTime? = null,
        var status: Int? = null
    ) {

        override fun toString(): String {
            val endpoint = url.split("/", limit=4).last() // keep URL after path
            val time = startedAt.format(ZDT_FORMATTER)

            val requestInfo = "$time $endpoint"
            val responseInfo = if (hasResponse()) " ($status in ${elapsedMs}ms)" else ""

            return "$requestInfo$responseInfo"
        }

        private fun hasResponse() =
            status != null && endedAt != null

        private val elapsedMs get() =
            endedAt?.let { ChronoUnit.MILLIS.between(startedAt, it) }
    }


    // We'll save requests by idempotency key, to avoid repetition on retry, and use a map variant
    // that preserves addition order:
    private val lastEndpoints = LinkedHashMap<IdempotencyKey, Entry>()


    @Synchronized
    fun getRecentRequests(): List<Entry> =
        lastEndpoints.values.toList().reversed() // most recent first

    @Synchronized
    fun reportRecentRequest(key: IdempotencyKey, url: String) {
        if (lastEndpoints.containsKey(key)) return

        lastEndpoints[key] = Entry(url, ZonedDateTime.now(ZoneOffset.UTC))

        // Ensure the record remains at a reasonable size:
        if (lastEndpoints.size > MAX_MEMORY_SIZE) {
            lastEndpoints.remove(lastEndpoints.keys.first())
        }
    }

    @Synchronized
    fun reportRecentResponse(key: IdempotencyKey, status: Int) {
        lastEndpoints[key]?.let {
            it.status = status
            it.endedAt = ZonedDateTime.now(ZoneOffset.UTC)
        }
    }
}