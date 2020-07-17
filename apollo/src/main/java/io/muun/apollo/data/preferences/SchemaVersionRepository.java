package io.muun.apollo.data.preferences;

import android.content.Context;
import com.f2prateek.rx.preferences.Preference;

import javax.inject.Inject;

public class SchemaVersionRepository extends BaseRepository {

    private static final String KEY_VERSION = "version";

    private final Preference<Integer> versionPreference;

    /**
     * Creates a repository.
     */
    @Inject
    public SchemaVersionRepository(Context context) {
        super(context);
        versionPreference = rxSharedPreferences.getInteger(KEY_VERSION);
    }

    @Override
    protected String getFileName() {
        return "schema_version";
    }

    /**
     * Returns the stored version, or 0 if none is stored.
     */
    public int getVersion() {
        return versionPreference.get();
    }

    public boolean hasVersion() {
        return versionPreference.isSet();
    }

    public void setVersion(int version) {
        versionPreference.set(version);
    }

}
