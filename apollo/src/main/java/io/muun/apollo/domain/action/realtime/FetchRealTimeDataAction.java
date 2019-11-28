package io.muun.apollo.domain.action.realtime;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.BlockchainHeightRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction0;
import io.muun.common.rx.RxHelper;

import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FetchRealTimeDataAction extends BaseAsyncAction0<Void> {

    private final HoustonClient houstonClient;
    private final FeeWindowRepository feeWindowRepository;
    private final ExchangeRateWindowRepository exchangeRateWindowRepository;
    private final BlockchainHeightRepository blockchainHeightRepository;

    /**
     * Update time-sensitive data, such as network fees and exchange rates.
     */
    @Inject
    public FetchRealTimeDataAction(HoustonClient houstonClient,
                                   FeeWindowRepository feeWindowRepository,
                                   ExchangeRateWindowRepository exchangeRateWindowRepository,
                                   BlockchainHeightRepository blockchainHeightRepository) {

        this.houstonClient = houstonClient;
        this.feeWindowRepository = feeWindowRepository;
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.blockchainHeightRepository = blockchainHeightRepository;
    }

    @Override
    public Observable<Void> action() {
        return Observable.defer(() -> {
            if (shouldSync()) {
                return forceSyncRealTimeData();

            } else {
                return Observable.just(null);
            }
        });
    }

    private Observable<Void> forceSyncRealTimeData() {
        Timber.d("[Sync] Updating fee/rates");

        return houstonClient.fetchRealTimeData()
                .doOnNext(realTimeData -> {
                    Timber.d("[Sync] Saving updated fee/rates");
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
