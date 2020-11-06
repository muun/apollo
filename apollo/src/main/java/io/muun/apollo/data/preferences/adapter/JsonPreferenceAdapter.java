package io.muun.apollo.data.preferences.adapter;

import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.serialization.SerializationUtils;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

public class JsonPreferenceAdapter<T> implements Preference.Adapter<T> {

    private final Class<T> valueClass;

    public JsonPreferenceAdapter(Class<T> valueClass) {
        this.valueClass = valueClass;
    }

    @Override
    public T get(@NonNull String key, @NonNull SharedPreferences preferences) {
        final String json = preferences.getString(key, null);

        if (json == null) {
            return null;
        }

        return SerializationUtils.deserializeJson(valueClass, json);
    }

    @Override
    public void set(@NonNull String k, @NonNull T v, @NonNull SharedPreferences.Editor editor) {
        editor.putString(k, SerializationUtils.serializeJson(valueClass, v));
    }
}
