package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.common.Optional;

import android.content.Context;
import rx.Observable;

import javax.inject.Inject;

public class ExchangeRateWindowRepository extends BaseRepository {

    private static final String KEY_WINDOW = "window";
    private static final String KEY_FIXED_WINDOW = "fixed_window";

    private final Preference<String> windowPreference;
    private final Preference<String> fixedWindowPreference;

    /**
     * Constructor.
     */
    @Inject
    public ExchangeRateWindowRepository(Context context, RepositoryRegistry repositoryRegistry) {
        super(context, repositoryRegistry);
        windowPreference = rxSharedPreferences.getString(KEY_WINDOW);
        fixedWindowPreference = rxSharedPreferences.getString(KEY_WINDOW);
    }

    @Override
    protected String getFileName() {
        return "exchange_rate_window";
    }

    /**
     * Return true if `fetch()` is safe to call.
     */
    public boolean isSet() {
        return windowPreference.isSet();
    }

    /**
     * Fetch an observable instance of the persisted exchange rates.
     */
    public Observable<ExchangeRateWindow> fetch() {
        return windowPreference.asObservable()
                .map(str -> SerializationUtils.deserializeJson(ExchangeRateWindow.class, str));
    }

    /**
     * Get the current exchange rates.
     */
    public ExchangeRateWindow fetchOne() {
        return fetch().toBlocking().first();
    }

    /**
     * Store the current exchange rates.
     */
    public void storeLatest(ExchangeRateWindow exchangeRateWindow) {
        windowPreference.set(
                SerializationUtils.serializeJson(ExchangeRateWindow.class, exchangeRateWindow)
        );
    }

    /**
     * Store an exchange rate window, that will, temporarily, be fixed for some specific flow.
     */
    public void storeAndFix(ExchangeRateWindow exchangeRateWindow) {
        fixedWindowPreference.set(
                SerializationUtils.serializeJson(ExchangeRateWindow.class, exchangeRateWindow)
        );
    }

    /**
     * Get a fixed exchange rate window, if any.
     */
    public Optional<ExchangeRateWindow> getFixedWindow() {
        if (fixedWindowPreference.isSet()) {
            final String json = fixedWindowPreference.get();
            return Optional.of(SerializationUtils.deserializeJson(ExchangeRateWindow.class, json));
        } else {
            return Optional.empty();
        }
    }
}
