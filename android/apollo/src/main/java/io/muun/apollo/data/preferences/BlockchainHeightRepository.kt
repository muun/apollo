package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import rx.Observable
import javax.inject.Inject

// Open for mockito to mock/spy
open class BlockchainHeightRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val BLOCKCHAIN_HEIGHT = "blockchain_height"
    }

    private val blockchainHeightPreference: Preference<Int>
        get() = rxSharedPreferences.getInteger(BLOCKCHAIN_HEIGHT, 0)

    override val fileName get() = "blockchain_height"

    /**
     * Fetch an observable instance of the persisted exchange rates.
     */
    fun fetch(): Observable<Int> {
        return blockchainHeightPreference.asObservable()
    }

    fun fetchLatest(): Int {
        return fetch().toBlocking().first()
    }

    /**
     * Store the current blockchain height.
     */
    fun store(blockchainHeight: Int) {
        blockchainHeightPreference.set(blockchainHeight)
    }
}