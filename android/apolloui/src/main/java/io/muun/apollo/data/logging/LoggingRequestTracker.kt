package io.muun.apollo.data.logging

import io.muun.common.api.error.Error
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit

// We'll use type aliases to help callers avoid mixing up parameters:
typealias IdempotencyKey = String

object LoggingRequestTracker {

    private const val MAX_MEMORY_SIZE = 5
    private val ZDT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val lock = this

    data class Entry(
        val url: String,
        val startedAt: ZonedDateTime,
        var endedAt: ZonedDateTime? = null,
        var status: Int? = null,
    ) {

        override fun toString(): String {
            val endpoint = url.split("/", limit = 4).last() // keep URL after path
            val time = startedAt.format(ZDT_FORMATTER)

            val requestInfo = "$time $endpoint"
            val responseInfo = if (hasResponse()) " ($status in ${elapsedMs}ms)" else ""

            return "$requestInfo$responseInfo"
        }

        private fun hasResponse() =
            status != null && endedAt != null

        private val elapsedMs
            get() =
                endedAt?.let { ChronoUnit.MILLIS.between(startedAt, it) }
    }


    // We'll save requests by idempotency key, to avoid repetition on retry, and use a map variant
    // that preserves addition order:
    private val lastEndpoints = LinkedHashMap<IdempotencyKey, Entry>()

    fun getRecentRequests(): List<Entry> =
        synchronized(lock) {
            lastEndpoints.values.toList().asReversed() // most recent first
        }

    fun reportRecentRequest(key: IdempotencyKey, url: String) {
        synchronized(lock) {
            if (lastEndpoints.containsKey(key)) return

            lastEndpoints[key] = Entry(url, ZonedDateTime.now(ZoneOffset.UTC))

            // Ensure the record remains at a reasonable size:
            if (lastEndpoints.size > MAX_MEMORY_SIZE) {
                lastEndpoints.remove(lastEndpoints.keys.first())
            }
        }
    }

    fun reportRecentErrorResponse(key: String, error: Error?) {
        synchronized(lock) {
            lastEndpoints[key]?.let {
                it.status = error?.errorCode?.code ?: 500
                it.endedAt = ZonedDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    fun reportRecentSuccessResponse(key: IdempotencyKey) {
        synchronized(lock) {
            lastEndpoints[key]?.let {
                it.status = 200
                it.endedAt = ZonedDateTime.now(ZoneOffset.UTC)
            }
        }
    }
}