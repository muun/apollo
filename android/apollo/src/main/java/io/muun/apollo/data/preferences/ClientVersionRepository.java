package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.common.Optional;

import android.content.Context;
import rx.Observable;

import javax.inject.Inject;

public class ClientVersionRepository extends BaseRepository {

    private static final String KEY_MIN_CLIENT_VERSION = "min_client_version";

    private final Preference<Integer> minClientVersionPreference;

    /**
     * Creates a repository for auth data.
     */
    @Inject
    public ClientVersionRepository(Context context, RepositoryRegistry repositoryRegistry) {
        super(context, repositoryRegistry);
        minClientVersionPreference = rxSharedPreferences.getInteger(KEY_MIN_CLIENT_VERSION, null);
    }

    @Override
    protected String getFileName() {
        return "clientVersion";
    }

    /**
     * Save minClientVersion in preferences.
     */
    public void storeMinClientVersion(int minClientVersion) {
        this.minClientVersionPreference.set(minClientVersion);
    }

    /**
     * Load minClientVersion from preferences, if present.
     */
    public Optional<Integer> getMinClientVersion() {
        return watchMinClientVersion().toBlocking().first();
    }

    public Observable<Optional<Integer>> watchMinClientVersion() {
        return minClientVersionPreference.asObservable()
                .map(Optional::ofNullable);
    }
}
