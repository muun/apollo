package io.muun.apollo.data.preferences;


import io.muun.apollo.data.preferences.rx.Preference;

import android.content.Context;

import javax.inject.Inject;

public class NotificationRepository extends BaseRepository {

    private static final String KEY_LAST_PROCESSED_ID = "last_processed_id";

    private final Preference<Long> lastProcessedIdPreference;

    @Inject
    public NotificationRepository(Context context) {
        super(context);
        lastProcessedIdPreference = rxSharedPreferences.getLong(KEY_LAST_PROCESSED_ID, 0L);
    }

    @Override
    protected String getFileName() {
        return "notifications";
    }

    public long getLastProcessedId() {
        return lastProcessedIdPreference.get();
    }

    public void setLastProcessedId(long lastConfirmedId) {
        lastProcessedIdPreference.set(lastConfirmedId);
    }
}
