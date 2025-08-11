package io.muun.apollo.domain.action.session

import android.content.Context
import io.muun.apollo.data.preferences.BaseRepository
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.data.preferences.RepositoryRegistry
import timber.log.Timber
import javax.inject.Inject

class ClearRepositoriesAction @Inject constructor(

    // Presentation
    private val context: Context,

    // Data
    private val repositoryRegistry: RepositoryRegistry,
    private val firebaseInstallationIdRepository: FirebaseInstallationIdRepository,
) {

    private val thirdPartyPreferencesToClear: List<String> = createThirdPartyPreferencesList()

    /**
     * Destroy all data in non-encrypted repositories.
     * Warning: this method is supposed to be used solely in PreferencesMigrationManager, since we
     * shouldn't be needing any "full wipe preference migration" anymore, this method is left just
     * for the sake of completeness.
     */
    fun clearAll() {
        clearForLogout()
        clearRepository(firebaseInstallationIdRepository)
    }

    /**
     * Destroy all data in non-encrypted repositories that should be cleared upon logout. We avoid
     * clearing some repositories on logout (e.g FcmTokenRepository).
     */
    fun clearForLogout() {
        for (repository in repositoryRegistry.repositoriesToClearOnLogout()) {
            clearRepository(repository)
        }
        for (fileName in thirdPartyPreferencesToClear) {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private fun clearRepository(repository: BaseRepository) {
        try {
            repository.clear()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun createThirdPartyPreferencesList(): List<String> {
        // NOTE: there is no reliable way of listing all SharedPreferences. The XML files are
        // *usually* located in a known directory, but some vendors change this. We cannot control
        // where the directory lies in a particular device, but we can know and control the actual
        // preferences created by Apollo's dependencies.

        // The following list was created by logging into Apollo, and listing the XML files created
        // in the data/shared_prefs folder of the application.

        // ON-RELEASE verify that the list matches the actual files added by our dependencies. This
        // won't crash if files do not exist, but we could miss some.
        return listOf(
            "TwitterAdvertisingInfoPreferences",
            "WebViewChromiumPrefs",
            "com.crashlytics.prefs",
            "com.crashlytics.sdk.android:answers:settings",
            "com.crashlytics.sdk.android.crashlytics-core"
                + ":com.crashlytics.android.core.CrashlyticsCore",
            "com.google.android.gms.appid",
            "com.google.android.gms.measurement.prefs",
            "io.fabric.sdk.android:fabric:io.fabric.sdk.android.Onboarding"
        )
    }

}