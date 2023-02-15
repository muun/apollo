package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

class PlayIntegrityNonceRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val PLAY_INTEGRITY_NONCE = "play_integrity_nonce"
    }

    private val noncePref: Preference<String> = rxSharedPreferences
        .getString(PLAY_INTEGRITY_NONCE)

    override fun getFileName() =
        "play_integrity_nonce"

    fun store(nonce: String?) {
        noncePref.set(nonce)
    }

    fun get(): String? =
        noncePref.get()
}