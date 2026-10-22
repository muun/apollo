package io.muun.apollo.data.logging

import java.util.Collections
import java.util.WeakHashMap

/**
 * Remembers which Throwable instances were already reported, so an error logged in several places
 * (e.g. by an AsyncAction and then by the presenter handling its state) is reported only once.
 * Keys are weak, so remembering an error doesn't keep it alive.
 */
class ReportedErrors {

    private val reported = Collections.newSetFromMap(WeakHashMap<Throwable, Boolean>())

    /**
     * Whether this is the first time the given error instance is reported. Marks it as reported.
     */
    @Synchronized
    fun markReported(error: Throwable): Boolean {
        return reported.add(error)
    }
}
