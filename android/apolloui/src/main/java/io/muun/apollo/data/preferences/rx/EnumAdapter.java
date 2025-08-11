package io.muun.apollo.data.preferences.rx;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import kotlin.NotImplementedError;

final class EnumAdapter<T extends Enum<T>> implements Preference.Adapter<T> {
    private final Class<T> enumClass;

    EnumAdapter(Class<T> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public T get(@NonNull String key,
                 @NonNull SharedPreferences preferences,
                 @NonNull T defaultValue) {
        final String value = preferences.getString(key, null);

        if (value == null) {
            return defaultValue;
        }

        return Enum.valueOf(enumClass, value);
    }

    @Override
    public T get(@NonNull String key, @NonNull SharedPreferences preferences) {
        // Should never be really used.
        throw new NotImplementedError();
    }

    @Override
    public void set(@NonNull String key,
                    @NonNull T value,
                    @NonNull SharedPreferences.Editor editor) {
        editor.putString(key, value.name());
    }
}
