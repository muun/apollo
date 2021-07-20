package io.muun.apollo.data.preferences;


import io.muun.apollo.data.preferences.rx.Preference;

import android.content.Context;

import javax.inject.Inject;

public class NotificationRepository extends BaseRepository {

    private static final String KEY_LAST_PROCESSED_ID = "last_processed_id";
    private static final String KEY_PROCESSING_FAILURES = "processing_failures";

    private final Preference<Long> lastProcessedIdPreference;
    private final Preference<Long> processingFailuresPreference;

    @Inject
    public NotificationRepository(Context context, RepositoryRegistry repositoryRegistry) {
        super(context, repositoryRegistry);
        lastProcessedIdPreference = rxSharedPreferences.getLong(KEY_LAST_PROCESSED_ID, 0L);
        processingFailuresPreference = rxSharedPreferences.getLong(KEY_PROCESSING_FAILURES, 0L);
    }

    @Override
    protected String getFileName() {
        return "notifications";
    }

    public long getLastProcessedId() {
        return lastProcessedIdPreference.get();
    }

    public void setLastProcessedId(long lastConfirmedId) {
        if (getLastProcessedId() < lastConfirmedId) {
            processingFailuresPreference.set(0L);
        }
        lastProcessedIdPreference.set(lastConfirmedId);
    }

    public void increaseProcessingFailures() {
        processingFailuresPreference.set(getProcessingFailures() + 1);
    }

    public long getProcessingFailures() {
        return processingFailuresPreference.get();
    }
}
