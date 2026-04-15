package io.muun.apollo.domain.utils

import io.muun.apollo.BuildConfig
import timber.log.Timber

class ChildTrace internal constructor(
    internal val label: String,
) {
    private val startTime: Long = System.currentTimeMillis()
    private var elapsedMs: Long? = null

    internal fun result(): Pair<String, String> =
        label to (elapsedMs?.toString() ?: "UNFINISHED")

    fun finish() {
        if (elapsedMs != null) {
            Timber.e("[TIMING] $label finished more than once")
            if (BuildConfig.DEBUG) {
                error("[TIMING] $label finished more than once")
            }
            return
        }
        elapsedMs = System.currentTimeMillis() - startTime
    }
}
