package io.muun.apollo.data.preferences.rx;

import android.content.SharedPreferences;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import rx.Observable;
import rx.functions.Action1;

/**
 *  A preference of type {@link T}. Instances can be created from {@link RxSharedPreferences}.
 */
public final class Preference<T> {

    /**
     *  Stores and retrieves instances of {@code T} in {@link SharedPreferences}.
     */
    public interface Adapter<T> {

        /**
         *  Retrieve the value for {@code key} from {@code preferences} or {@code defaultValue}
         *  if the preference is unset, or was set to {@code null}.
         */
        default T get(@NonNull String key,
                      @NonNull SharedPreferences preferences,
                      @NonNull T defaultValue) {
            return get(key, preferences); // Default impl, subclasses that need it can override
        }

        /**
         *  Retrieve the value for {@code key} from {@code preferences}.
         */
        T get(@NonNull String key, @NonNull SharedPreferences preferences);

        /**
         * Store non-null {@code value} for {@code key} in {@code editor}.
         *
         * <p>Note: Implementations <b>must not</b> call {@code commit()} or {@code apply()} on
         * {@code editor}.
         */
        void set(@NonNull String key, @NonNull T value, @NonNull SharedPreferences.Editor editor);
    }

    private final SharedPreferences preferences;
    private final String key;
    private final T defaultValue;
    private final Adapter<T> adapter;
    private final Observable<T> values;

    Preference(SharedPreferences preferences, final String key, T defaultValue, Adapter<T> adapter,
               Observable<String> keyChanges) {
        this.preferences = preferences;
        this.key = key;
        this.defaultValue = defaultValue;
        this.adapter = adapter;
        this.values = keyChanges
                .filter(key::equals)
                .startWith("<init>") // Dummy value to trigger initial load.
                .onBackpressureLatest()
                .map(ignored -> get());
    }

    /**
     * The key for which this preference will store and retrieve values.
     */
    @NonNull
    public String key() {
        return key;
    }

    /**
     * The value used if none is stored. May be {@code null}.
     */
    @Nullable
    public T defaultValue() {
        return defaultValue;
    }

    /**
     * Retrieve the current value for this preference. Returns {@link #defaultValue()} if no value
     * is set.
     */
    @Nullable
    public T get() {
        if (!preferences.contains(key)) {
            return defaultValue;
        }
        return adapter.get(key, preferences, defaultValue);
    }

    /**
     * Change this preference's stored value to {@code value}. A value of {@code null} will delete
     * the preference.
     */
    public void set(@Nullable T value) {
        final SharedPreferences.Editor editor = preferences.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            adapter.set(key, value, editor);
        }
        editor.apply();
    }

    /**
     * Change this preference's stored value to {@code value} synchronously and return true if
     * new value was successfully written.
     */
    public boolean setNow(@Nullable T value) {
        final SharedPreferences.Editor editor = preferences.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            adapter.set(key, value, editor);
        }
        return editor.commit();
    }

    /**
     * Returns true if this preference has a stored value.
     */
    public boolean isSet() {
        return preferences.contains(key);
    }

    /**
     * Delete the stored value for this preference, if any. */
    public void delete() {
        set(null);
    }

    /**
     * Observe changes to this preference. The current value or {@link #defaultValue()} will be
     * emitted on first subscribe.
     */
    @CheckResult
    @NonNull
    public Observable<T> asObservable() {
        return values;
    }

    /**
     * An action which stores a new value for this preference. Passing {@code null} will delete the
     * preference.
     */
    @CheckResult
    @NonNull
    public Action1<? super T> asAction() {
        return this::set;
    }
}
