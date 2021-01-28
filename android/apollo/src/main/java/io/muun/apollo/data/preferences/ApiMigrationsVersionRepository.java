package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.rx.Preference;

import android.content.Context;

import javax.inject.Inject;

public class ApiMigrationsVersionRepository extends BaseRepository {

    private static final String KEY_VERSION = "version";

    private final Preference<Integer> versionPreference;

    /**
     * Creates a repository.
     */
    @Inject
    public ApiMigrationsVersionRepository(Context context) {
        super(context);
        versionPreference = rxSharedPreferences.getInteger(KEY_VERSION);
    }

    @Override
    protected String getFileName() {
        return "api_migrations_version";
    }

    /**
     * Returns the stored version, or 0 if none is stored.
     */
    public int getVersion() {
        return versionPreference.get();
    }

    public void setVersion(int version) {
        versionPreference.set(version);
    }

}
