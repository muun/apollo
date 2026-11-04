package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

class ConnectedToWebsiteDialogSeenRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val SEEN_PROVIDER_UUIDS = "seen_provider_uuids"
    }

    private val seenProviderUuidsPref: Preference<Set<String>>
        get() = rxSharedPreferences.getStringSet(SEEN_PROVIDER_UUIDS)

    override val fileName get() = "connected_to_website_dialog_seen"

    fun isSeenFor(providerUuid: String): Boolean {
        return requireNotNull(seenProviderUuidsPref.get()).contains(providerUuid)
    }

    fun markSeenFor(providerUuid: String) {
        val seenProviderUuids = requireNotNull(seenProviderUuidsPref.get())

        seenProviderUuidsPref.set(seenProviderUuids + providerUuid)
    }
}
