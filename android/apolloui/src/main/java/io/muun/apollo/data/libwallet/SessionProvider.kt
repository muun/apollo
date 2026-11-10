package io.muun.apollo.data.libwallet

import app_provided_data.SessionUser
import app_provided_data.SessionUserResponse
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.selector.UserSelector
import timber.log.Timber
import javax.inject.Inject

/**
 * Tells libwallet about the app's session. Errors are logged before being rethrown, since libwallet
 * only gets their message.
 */
class SessionProvider @Inject constructor(
    private val userActions: UserActions,
    private val userSelector: UserSelector,
): app_provided_data.SessionProvider {

    override fun isLoggedIn(): Boolean {
        try {
            return userActions.isLoggedIn
        } catch (e: Throwable) {
            Timber.e(e)
            throw e
        }
    }

    override fun getUser(): SessionUserResponse {
        try {
            return SessionUserResponse().apply {
                user = userSelector.getHoustonId()?.let { houstonId ->
                    SessionUser().apply { houstonID = houstonId }
                }
            }
        } catch (e: Throwable) {
            Timber.e(e)
            throw e
        }
    }
}
