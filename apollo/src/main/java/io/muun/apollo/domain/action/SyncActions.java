package io.muun.apollo.domain.action;

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.common.model.ExchangeRateProvider;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SyncActions {

    private final ExchangeRateWindowRepository exchangeRateWindowRepository;

    /**
     * Constructor.
     */
    @Inject
    public SyncActions(ExchangeRateWindowRepository exchangeRateWindowRepository) {
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
    }

    /**
     * Watch the real-time exchange rate between currencies.
     */
    public Observable<ExchangeRateProvider> watchExchangeRates() {
        return exchangeRateWindowRepository.fetch()
                .map(rateWindow -> new ExchangeRateProvider(rateWindow.rates));
    }
}
