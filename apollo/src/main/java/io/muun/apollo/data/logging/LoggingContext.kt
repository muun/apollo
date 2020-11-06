package io.muun.apollo.data.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics


object LoggingContext {

    @JvmStatic
    var sendToCrashlytics = true  // default for production

    @JvmStatic
    var sendToLogcat = false // default for production

    @JvmStatic
    fun configure(email: String?, userId: String) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setUserId(userId)
        crashlytics.setCustomKey("email", email ?: "unknown")

        // TODO: use setUserEmail, and grab Houston session UUID to attach it
    }
}