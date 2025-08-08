package io.muun.apollo.data.preferences.rx;

import io.muun.common.Optional;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

final class OptionalEnumAdapter<T extends Enum<T>> implements Preference.Adapter<Optional<T>> {

    private final Class<T> enumClass;

    OptionalEnumAdapter(Class<T> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public Optional<T> get(@NonNull String key, @NonNull SharedPreferences preferences) {
        final String value = preferences.getString(key, null);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(Enum.valueOf(enumClass, value));
    }

    @Override
    public void set(@NonNull String key,
                    @NonNull Optional<T> value,
                    @NonNull SharedPreferences.Editor editor) {
        value.ifPresent(enumValue -> editor.putString(key, enumValue.name()))
                .orElse(() -> editor.putString(key, null));
    }
}
