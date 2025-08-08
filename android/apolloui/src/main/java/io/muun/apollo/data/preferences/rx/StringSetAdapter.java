package io.muun.apollo.data.preferences.rx;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import java.util.Set;

@TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
final class StringSetAdapter implements Preference.Adapter<Set<String>> {
    static final StringSetAdapter INSTANCE = new StringSetAdapter();

    @Override
    public Set<String> get(@NonNull String key, @NonNull SharedPreferences preferences) {
        return preferences.getStringSet(key, null);
    }

    @Override
    public void set(@NonNull String key, @NonNull Set<String> value,
                    @NonNull SharedPreferences.Editor editor) {
        editor.putStringSet(key, value);
    }
}