package io.muun.apollo.domain.action;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.BlockchainHeightRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.rx.RxHelper;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SyncActions {

    private final ExchangeRateWindowRepository exchangeRateWindowRepository;
    private final FeeWindowRepository feeWindowRepository;
    private final BlockchainHeightRepository blockchainHeightRepository;

    private final HoustonClient houstonClient;

    public final AsyncAction0<Void> syncRealTimeDataAction;

    /**
     * Constructor.
     */
    @Inject
    public SyncActions(ExchangeRateWindowRepository exchangeRateWindowRepository,
                       FeeWindowRepository feeWindowRepository,
                       BlockchainHeightRepository blockchainHeightRepository,
                       HoustonClient houstonClient,
                       AsyncActionStore asyncActionStore) {

        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.feeWindowRepository = feeWindowRepository;
        this.blockchainHeightRepository = blockchainHeightRepository;
        this.houstonClient = houstonClient;

        this.syncRealTimeDataAction = asyncActionStore.get("realTime/sync", this::syncRealTimeData);
    }

    /**
     * Watch the real-time exchange rate between currencies.
     */
    public Observable<ExchangeRateProvider> watchExchangeRates() {
        return exchangeRateWindowRepository.fetch()
                .map(rateWindow -> new ExchangeRateProvider(rateWindow.rates));
    }

    /**
     * Fetch the last real-time data from Houston, if our copy is stale.
     */
    public Observable<Void> syncRealTimeData() {
        return Observable.defer(() -> {
            if (shouldSync()) {
                return forceSyncRealTimeData();
            } else {
                return Observable.just(null);
            }
        });
    }

    private Observable<Void> forceSyncRealTimeData() {
        Logger.debug("[Sync] Updating fee/rates");

        return houstonClient.fetchRealTimeData()
                .doOnNext(realTimeData -> {
                    Logger.debug("[Sync] Saving updated fee/rates");
                    feeWindowRepository.store(realTimeData.feeWindow);
                    exchangeRateWindowRepository.store(realTimeData.exchangeRateWindow);
                    blockchainHeightRepository.store(realTimeData.currentBlockchainHeight);
                })
                .map(RxHelper::toVoid);
    }

    private boolean shouldSync() {
        final boolean isFeeRecent = feeWindowRepository.isSet()
                && feeWindowRepository.fetchOne().isRecent();

        final boolean isExchangeRateRecent = exchangeRateWindowRepository.isSet()
                && exchangeRateWindowRepository.fetchOne().isRecent();

        return (!isFeeRecent || !isExchangeRateRecent);
    }
}
