package io.muun.apollo.data.preferences.rx;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

final class LongAdapter implements Preference.Adapter<Long> {
    static final LongAdapter INSTANCE = new LongAdapter();

    @Override
    public Long get(@NonNull String key, @NonNull SharedPreferences preferences) {
        return preferences.getLong(key, 0L);
    }

    @Override
    public void set(@NonNull String key, @NonNull Long value,
                    @NonNull SharedPreferences.Editor editor) {
        editor.putLong(key, value);
    }
}
