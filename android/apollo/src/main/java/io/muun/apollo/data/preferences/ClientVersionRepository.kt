package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.common.Optional
import rx.Observable
import javax.inject.Inject

class ClientVersionRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_MIN_CLIENT_VERSION = "min_client_version"
    }

    private val minClientVersionPreference: Preference<Int>
        get() = rxSharedPreferences.getInteger(KEY_MIN_CLIENT_VERSION, null)

    override val fileName get() = "clientVersion"

    /**
     * Save minClientVersion in preferences.
     */
    fun storeMinClientVersion(minClientVersion: Int) {
        minClientVersionPreference.set(minClientVersion)
    }

    /**
     * Load minClientVersion from preferences, if present.
     */
    // TODO this optional can probably be removed.
    val minClientVersion: Optional<Int>
        get() = watchMinClientVersion().toBlocking().first()

    /**
     * Return an Observable of MinClientVersion preference.
     */
    fun watchMinClientVersion(): Observable<Optional<Int>> {
        return minClientVersionPreference.asObservable()
            .map { value: Int? -> Optional.ofNullable(value) }
    }
}