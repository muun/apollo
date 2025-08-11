package io.muun.apollo.data.preferences.rx;

import io.muun.common.Optional;
import io.muun.common.utils.Preconditions;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import rx.Observable;
import rx.subscriptions.Subscriptions;

import java.util.Collections;
import java.util.Set;

/**
 * A factory for reactive {@link Preference} objects.
 */
public final class RxSharedPreferences {
    private static final Float DEFAULT_FLOAT = (float) 0;
    private static final Integer DEFAULT_INTEGER = 0;
    private static final Boolean DEFAULT_BOOLEAN = Boolean.FALSE;
    private static final Long DEFAULT_LONG = 0L;

    /**
     * Create an instance of {@link RxSharedPreferences} for {@code preferences}.
     */
    @CheckResult
    @NonNull
    public static RxSharedPreferences create(@NonNull SharedPreferences preferences) {
        Preconditions.checkNotNull(preferences, "preferences == null");
        return new RxSharedPreferences(preferences);
    }

    private final SharedPreferences preferences;
    private final Observable<String> keyChanges;

    private RxSharedPreferences(final SharedPreferences preferences) {
        this.preferences = preferences;
        this.keyChanges = Observable.create((Observable.OnSubscribe<String>) subscriber -> {
            final SharedPreferences.OnSharedPreferenceChangeListener listener =
                    (preferences1, key) -> subscriber.onNext(key);

            preferences.registerOnSharedPreferenceChangeListener(listener);

            subscriber.add(Subscriptions.create(
                    () -> preferences.unregisterOnSharedPreferenceChangeListener(listener))
            );
        }).share();
    }

    /**
     * Create a boolean preference for {@code key}. Default is {@code false}.
     */
    @CheckResult
    @NonNull
    public Preference<Boolean> getBoolean(@NonNull String key) {
        return getBoolean(key, DEFAULT_BOOLEAN);
    }

    /**
     * Create a boolean preference for {@code key} with a default of {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public Preference<Boolean> getBoolean(@NonNull String key, @Nullable Boolean defaultValue) {
        Preconditions.checkNotNull(key, "key == null");
        return new Preference<>(preferences,
                key,
                defaultValue,
                BooleanAdapter.INSTANCE,
                keyChanges);
    }

    /**
     * Create an enum preference for {@code key} that allows null values.
     */
    @CheckResult
    @NonNull
    public <T extends Enum<T>> Preference<Optional<T>> getOptionalEnum(@NonNull String key,
                                                                       @NonNull Class<T> enumClss) {
        Preconditions.checkNotNull(key, "key == null");
        Preconditions.checkNotNull(enumClss, "enumClass == null");
        final Preference.Adapter<Optional<T>> adapter = new OptionalEnumAdapter<T>(enumClss);
        return new Preference<>(preferences, key, null, adapter, keyChanges);
    }

    /**
     * Create an enum preference for {@code key} with a default of {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public <T extends Enum<T>> Preference<T> getEnum(@NonNull String key,
                                                     @NonNull T defaultValue,
                                                     @NonNull Class<T> enumClass) {
        Preconditions.checkNotNull(key, "key == null");
        Preconditions.checkNotNull(enumClass, "enumClass == null");
        final Preference.Adapter<T> adapter = new EnumAdapter<>(enumClass);
        return new Preference<>(preferences, key, defaultValue, adapter, keyChanges);
    }

    /**
     * Create a float preference for {@code key}. Default is {@code 0}.
     */
    @CheckResult
    @NonNull
    public Preference<Float> getFloat(@NonNull String key) {
        return getFloat(key, DEFAULT_FLOAT);
    }

    /**
     * Create a float preference for {@code key} with a default of {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public Preference<Float> getFloat(@NonNull String key, @Nullable Float defaultValue) {
        Preconditions.checkNotNull(key, "key == null");
        return new Preference<>(preferences, key, defaultValue, FloatAdapter.INSTANCE, keyChanges);
    }

    /**
     * Create an integer preference for {@code key}. Default is {@code 0}.
     */
    @CheckResult
    @NonNull
    public Preference<Integer> getInteger(@NonNull String key) {
        return getInteger(key, DEFAULT_INTEGER);
    }

    /**
     * Create an integer preference for {@code key} with a default of {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public Preference<Integer> getInteger(@NonNull String key, @Nullable Integer defaultValue) {
        Preconditions.checkNotNull(key, "key == null");
        return new Preference<>(preferences,
                key,
                defaultValue,
                IntegerAdapter.INSTANCE,
                keyChanges);
    }

    /**
     * Create a long preference for {@code key}. Default is {@code 0}.
     */
    @CheckResult
    @NonNull
    public Preference<Long> getLong(@NonNull String key) {
        return getLong(key, DEFAULT_LONG);
    }

    /**
     * Create a long preference for {@code key} with a default of {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public Preference<Long> getLong(@NonNull String key, @Nullable Long defaultValue) {
        Preconditions.checkNotNull(key, "key == null");
        return new Preference<>(preferences, key, defaultValue, LongAdapter.INSTANCE, keyChanges);
    }

    /**
     * Create a preference of type {@code T} for {@code key}. Default is {@code null}.
     */
    @CheckResult
    @NonNull
    public <T> Preference<T> getObject(@NonNull String key,
                                       @NonNull Preference.Adapter<T> adapter) {
        return getObject(key, null, adapter);
    }

    /**
     * Create a preference for type {@code T} for {@code key} with a default of
     * {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public <T> Preference<T> getObject(@NonNull String key, @Nullable T defaultValue,
                                       @NonNull Preference.Adapter<T> adapter) {
        Preconditions.checkNotNull(key, "key == null");
        Preconditions.checkNotNull(adapter, "adapter == null");
        return new Preference<>(preferences, key, defaultValue, adapter, keyChanges);
    }

    /**
     * Create a string preference for {@code key}. Default is {@code null}.
     */
    @CheckResult
    @NonNull
    public Preference<String> getString(@NonNull String key) {
        return getString(key, null);
    }

    /**
     * Create a string preference for {@code key} with a default of {@code defaultValue}.
     */
    @CheckResult
    @NonNull
    public Preference<String> getString(@NonNull String key, @Nullable
            String defaultValue) {
        Preconditions.checkNotNull(key, "key == null");
        return new Preference<>(preferences, key, defaultValue, StringAdapter.INSTANCE, keyChanges);
    }

    /**
     * Create a string set preference for {@code key}. Default is an empty set.
     */
    @TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
    @CheckResult
    @NonNull
    public Preference<Set<String>> getStringSet(@NonNull String key) {
        return getStringSet(key, Collections.emptySet());
    }

    /**
     * Create a string set preference for {@code key} with a default of {@code defaultValue}.
     */
    @TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
    @CheckResult
    @NonNull
    public Preference<Set<String>> getStringSet(@NonNull String key,
                                                @NonNull Set<String> defaultValue) {
        Preconditions.checkNotNull(key, "key == null");
        return new Preference<>(preferences,
                key,
                defaultValue,
                StringSetAdapter.INSTANCE,
                keyChanges);
    }
}
