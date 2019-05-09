package io.muun.apollo.data.preferences.adapter;

import io.muun.apollo.data.serialization.SerializationUtils;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import com.f2prateek.rx.preferences.Preference;

public class JsonPreferenceAdapter<T> implements Preference.Adapter<T> {

    public static final JsonPreferenceAdapter<Object> GENERIC =
            new JsonPreferenceAdapter<>(Object.class);

    private final Class<T> valueClass;

    public JsonPreferenceAdapter(Class<T> valueClass) {
        this.valueClass = valueClass;
    }

    @Override
    public T get(String key, SharedPreferences preferences) {
        final String json = preferences.getString(key, null);

        if (json == null) {
            return null;
        }

        return SerializationUtils.deserializeJson(valueClass, json);
    }

    @Override
    public void set(String key, @Nullable T value, SharedPreferences.Editor editor) {
        if (value == null) {
            editor.remove(key);
            return;
        }

        editor.putString(key, SerializationUtils.serializeJson(valueClass, value));
    }
}
