package io.muun.apollo.data.logging

import com.crashlytics.android.Crashlytics

object LoggingContext {

    @JvmStatic
    var sendToCrashlytics = true  // default for production

    @JvmStatic
    var sendToLogcat = false // default for production

    @JvmStatic
    fun configure(email: String?, userId: String?) {
        Crashlytics.setUserIdentifier(userId)
        Crashlytics.setUserName(email)

        // TODO: use setUserEmail, and grab Houston session UUID to attach it
    }

}