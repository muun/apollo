package io.muun.apollo.data.logging

object LoggingContext {

    /**
     * Log errors to the Crashlytics.
     */
    @JvmStatic
    var sendToCrashlytics = true  // default for production

    /**
     * Log errors to the system logs.
     */
    @JvmStatic
    var sendToLogcat = false // default for production

    @JvmStatic
    var locale: String = "UNSET" // easily track and attach user's locale to muun errors and reports
}