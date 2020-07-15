package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.ExchangeRateWindow;

import android.content.Context;
import rx.Observable;

import javax.inject.Inject;

public class ExchangeRateWindowRepository extends BaseRepository {

    private static final String KEY_WINDOW = "window";

    private final Preference<String> windowPreference;

    /**
     * Constructor.
     */
    @Inject
    public ExchangeRateWindowRepository(Context context) {
        super(context);
        windowPreference = rxSharedPreferences.getString(KEY_WINDOW);
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

    public ExchangeRateWindow fetchOne() {
        return fetch().toBlocking().first();
    }

    /**
     * Store the current exchange rates.
     */
    public void store(ExchangeRateWindow exchangeRateWindow) {
        windowPreference.set(
                SerializationUtils.serializeJson(ExchangeRateWindow.class, exchangeRateWindow)
        );
    }
}
