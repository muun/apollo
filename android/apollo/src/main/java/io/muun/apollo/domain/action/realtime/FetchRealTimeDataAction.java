package io.muun.apollo.domain.action.realtime;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.BlockchainHeightRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeaturesRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.ForwardingPoliciesRepository;
import io.muun.apollo.data.preferences.MinFeeRateRepository;
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
    private final ForwardingPoliciesRepository forwardingPoliciesRepository;
    private final MinFeeRateRepository minFeeRateRepository;
    private final FeaturesRepository featuresRepository;

    /**
     * Update time-sensitive data, such as network fees and exchange rates.
     */
    @Inject
    public FetchRealTimeDataAction(
            final HoustonClient houstonClient,
            final FeeWindowRepository feeWindowRepository,
            final ExchangeRateWindowRepository exchangeRateWindowRepository,
            final BlockchainHeightRepository blockchainHeightRepository,
            final ForwardingPoliciesRepository forwardingPoliciesRepository,
            final MinFeeRateRepository minFeeRateRepository,
            final FeaturesRepository featuresRepository) {

        this.houstonClient = houstonClient;
        this.feeWindowRepository = feeWindowRepository;
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.blockchainHeightRepository = blockchainHeightRepository;
        this.forwardingPoliciesRepository = forwardingPoliciesRepository;
        this.minFeeRateRepository = minFeeRateRepository;
        this.featuresRepository = featuresRepository;
    }

    /**
     * Force re-fetch of Houston's RealTimeData, bypassing any local cache logic.
     */
    public void runForced() {
        super.run(Observable.defer(this::forceSyncRealTimeData));
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
                    exchangeRateWindowRepository.storeLatest(realTimeData.exchangeRateWindow);
                    blockchainHeightRepository.store(realTimeData.currentBlockchainHeight);
                    forwardingPoliciesRepository.store(realTimeData.forwardingPolicies);
                    minFeeRateRepository.store(realTimeData.minFeeRateInWeightUnits);
                    featuresRepository.store(realTimeData.features);
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
