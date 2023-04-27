package io.muun.apollo.domain.action.integrity

import io.muun.apollo.domain.errors.PlayIntegrityError
import io.muun.common.rx.ExponentialBackoffRetry
import java.util.concurrent.TimeUnit

class GooglePlayIntegrityRetryPolicy(
    baseIntervalInSecs: Long,
    maxRetries: Int,
    private val retryErrorCodes: List<Int>,
) : ExponentialBackoffRetry(baseIntervalInSecs, TimeUnit.SECONDS, maxRetries, null) {

    /**
     * Subclasses can override this method to decide whether this strategy should retry after a
     * specific error, or abort the sequence. By default, all errors of type {retryErrorTypes} are
     * retried.
     */
    override fun shouldRetry(error: Throwable): Boolean {
        if (error is PlayIntegrityError) {
            return error.getCode() != null && retryErrorCodes.contains(error.getCode())
        }

        return false
    }

    /**
     * Workaround for overriding getLastError but remove ugly Optional<> Java return value.
     */
    fun lastError(): Throwable? {
        return super.getLastError().orElse(null)
    }
}