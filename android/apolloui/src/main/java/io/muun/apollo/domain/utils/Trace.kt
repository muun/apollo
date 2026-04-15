package io.muun.apollo.domain.utils

import io.muun.apollo.BuildConfig
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import timber.log.Timber

/** A running timing measurement. Call [finish] or use `use {}` to report the elapsed time. */
class Trace internal constructor(
    private val label: String,
    private val analytics: Analytics,
) : AutoCloseable {

    private val startTime: Long = System.currentTimeMillis()
    private var finished = false

    private val children = mutableListOf<ChildTrace>()

    /** Create a child trace. Call [ChildTrace.finish] when the child operation completes. */
    fun child(label: String): ChildTrace {
        return ChildTrace(label).also { children.add(it) }
    }

    /** Report the elapsed time to analytics. */
    fun finish() {
        if (finished) {
            Timber.e("[TIMING] $label finished more than once")
            if (BuildConfig.DEBUG) {
                error("[TIMING] $label finished more than once")
            }
            return
        }

        finished = true
        val elapsed = System.currentTimeMillis() - startTime
        val childMap = children.associate { it.result() }
        analytics.report(AnalyticsEvent.E_TIME_TRACKER(label, elapsed, childMap))
    }

    override fun close() = finish()
}
