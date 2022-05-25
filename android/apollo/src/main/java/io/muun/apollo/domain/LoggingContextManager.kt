package io.muun.apollo.domain

import android.content.Context
import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.logging.LoggingContext
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.utils.locale
import io.muun.common.Optional
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggingContextManager @Inject constructor(
    private val userRepository: UserRepository,
    private val context: Context,
) {

    /**
     * Set up Crashlytics metadata.
     */
    fun setupCrashlytics() {
        val maybeUser: Optional<User> = userRepository.fetchOneOptional()

        if (!maybeUser.isPresent) {
            return  // If no LOGGED-IN user do nothing (we handle sign-in flow on its own)
        }

        val user = maybeUser.get()
        Crashlytics.configure(user.email.orElse(null), user.hid.toString())

        LoggingContext.locale = context.locale().toString()
    }
}