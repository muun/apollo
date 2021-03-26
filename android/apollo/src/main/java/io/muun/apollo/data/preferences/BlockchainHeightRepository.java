package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.rx.Preference;

import android.content.Context;
import rx.Observable;

import javax.inject.Inject;

public class BlockchainHeightRepository extends BaseRepository {

    private static final String BLOCKCHAIN_HEIGHT = "blockchain_height";
    private final Preference<Integer> blockchainHeightPreference;

    /**
     * Constructor.
     */
    @Inject
    public BlockchainHeightRepository(Context context, RepositoryRegistry repositoryRegistry) {
        super(context, repositoryRegistry);
        blockchainHeightPreference = rxSharedPreferences.getInteger(BLOCKCHAIN_HEIGHT, 0);
    }

    @Override
    protected String getFileName() {
        return "blockchain_height";
    }

    /**
     * Return true if `fetch()` is safe to call.
     */
    public boolean isSet() {
        return blockchainHeightPreference.isSet();
    }

    /**
     * Fetch an observable instance of the persisted exchange rates.
     */
    public Observable<Integer> fetch() {
        return blockchainHeightPreference.asObservable();
    }

    public int fetchLatest() {
        return fetch().toBlocking().first();
    }

    /**
     * Store the current blockchain height.
     */
    public void store(int blockchainHeight) {
        blockchainHeightPreference.set(blockchainHeight);
    }
}
