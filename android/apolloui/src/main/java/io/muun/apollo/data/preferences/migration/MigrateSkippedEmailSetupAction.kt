package io.muun.apollo.data.preferences.migration

import io.muun.apollo.data.preferences.UserPreferencesRepository
import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import javax.inject.Inject

class MigrateSkippedEmailSetupAction @Inject constructor(
    private val updateUserPreferences: UpdateUserPreferencesAction,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    /**
     * This migrations is a "best effort", we start storing locally in userPreference and try
     * updating remotely though we accept that it may fail. If remote update fails, hopefully, if
     * user performs another action the userPreferences may yet be updated. Otherwise, we accept
     * this is imperfect.
     */
    fun run(skippedEmailSetup: Boolean) {

        val updatedPrefs = userPreferencesRepository.updateSkippedEmail(skippedEmailSetup)

        if (updatedPrefs.skippedEmailSetup) {
            updateUserPreferences.run { updatedPrefs }
        }
    }
}