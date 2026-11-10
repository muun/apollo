package io.muun.apollo.domain.libwallet

import android.util.Log
import app_provided_data.AppLogSink
import io.muun.apollo.data.external.Globals
import timber.log.Timber

class LibwalletLogAdapter : AppLogSink {

    companion object {
        // The levels of Go's slog package.
        private const val SLOG_DEBUG = -4L
        private const val SLOG_INFO = 0L
        private const val SLOG_WARN = 4L
    }

    override fun getMinLogLevel(): Long {
        // This is passed to go code.
        return if (Globals.INSTANCE.isDogfood || Globals.INSTANCE.isDebug) {
            SLOG_DEBUG
        } else {
            SLOG_INFO
        }
    }

    override fun log(level: Long, message: String, file: String, line: Long, function: String) {
        Timber.tag("$file:$function").log(androidPriority(level), message)
    }

    /**
     * Return the android log priority of a slog level.
     */
    private fun androidPriority(level: Long): Int {
        return when (level) {
            SLOG_DEBUG -> Log.DEBUG
            SLOG_INFO -> Log.INFO
            SLOG_WARN -> Log.WARN
            // If we're not sure, it's an error.
            else -> Log.ERROR
        }
    }
}
