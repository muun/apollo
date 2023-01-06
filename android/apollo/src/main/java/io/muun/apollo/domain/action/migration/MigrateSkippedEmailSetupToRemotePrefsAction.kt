package io.muun.apollo.domain.action.migration

import android.content.Context
import android.content.SharedPreferences
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.data.preferences.migration.MigrateSkippedEmailSetupAction
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.selector.UserSelector
import rx.Observable
import javax.inject.Inject

class MigrateSkippedEmailSetupToRemotePrefsAction @Inject constructor(
    private val context: Context,
    private val migrateSkippedEmailSetup: MigrateSkippedEmailSetupAction,
    private val userSel: UserSelector
) : BaseAsyncAction0<Void>() {

    /**
     * Note: we are INTENTIONALLY not handling loading or failure states for this migration.
     * See: {@link MigrateSkippedEmailSetupAction}
     */
    override fun action(): Observable<Void> {

        val maybeUser = userSel.getOptional()
        // If no logged in user, do nothing.
        // Note: If run as an ApiMigration, this will never run if no user is logged in (
        // api migrations are only run if user is logged in) . Still... defensive programming
        if (!maybeUser.isPresent) {
            return Observable.just(null)
        }

        val localUserPrefs: SharedPreferences = context
            .getSharedPreferences("user", Context.MODE_PRIVATE)

        // This is an extra precaution so as to not migrate incorrect/invalid local prefs that may
        // have stayed on/true after successful email/password setup.
        if (!maybeUser.get().hasPassword) {

            val userHasSkippedEmailSetup = localUserPrefs
                .getBoolean("email_setup_skipped_key", false)

            migrateSkippedEmailSetup.run(userHasSkippedEmailSetup)
        }

        localUserPrefs.edit().remove("email_setup_skipped_key").apply()

        return Observable.just(null)
    }
}