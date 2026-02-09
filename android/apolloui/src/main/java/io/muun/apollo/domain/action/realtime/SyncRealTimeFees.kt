package io.muun.apollo.domain.action.realtime

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.MinFeeRateRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.model.feebump.FeeBumpRefreshPolicy
import io.muun.apollo.domain.libwallet.FeeBumpFunctionsProvider
import io.muun.apollo.domain.model.feebump.FeeBumpFunctions
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.RealTimeFees
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.common.Rules
import io.muun.common.rx.RxHelper
import rx.Observable
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRealTimeFees @Inject constructor(
    private val houstonClient: HoustonClient,
    private val feeWindowRepository: FeeWindowRepository,
    private val minFeeRateRepository: MinFeeRateRepository,
    private val transactionSizeRepository: TransactionSizeRepository,
    private val featureSelector: FeatureSelector,
    private val feeBumpFunctionsProvider: FeeBumpFunctionsProvider,
) {

    companion object {
        private const val THROTTLE_INTERVAL_IN_MILLISECONDS: Long = 10 * 1000
    }

    private var lastSyncTime: Date = Date(0) // Init with distant past

    fun sync(refreshPolicy: FeeBumpRefreshPolicy): Observable<Void> {
        if (!featureSelector.get(MuunFeature.EFFECTIVE_FEES_CALCULATION)) {
            return Observable.just(null)
        }

        Timber.i("[Sync] Updating realTime fees data: ${refreshPolicy.value}")

        transactionSizeRepository.nextTransactionSize?.let { nts ->
            if (nts.unconfirmedUtxos.isEmpty()) {
                // If there are no unconfirmed UTXOs, it means there are no fee bump functions.
                // Remove the fee bump functions by storing an empty list.
                val emptyFeeBumpFunctions = FeeBumpFunctions("", emptyList())
                feeBumpFunctionsProvider.persistFeeBumpFunctions(emptyFeeBumpFunctions, refreshPolicy)
            }
            return houstonClient.fetchRealTimeFees(nts.unconfirmedUtxos, refreshPolicy)
                .doOnNext { realTimeFees: RealTimeFees ->

                    Timber.i("[Sync] Saving updated realTime fees: ${refreshPolicy.value}")
                    storeFeeData(realTimeFees)
                    feeBumpFunctionsProvider.persistFeeBumpFunctions(
                        realTimeFees.feeBumpFunctions,
                        refreshPolicy
                    )
                    lastSyncTime = Date()
                }
                .map(RxHelper::toVoid)
        }

        Timber.e("syncRealTimeFees was called without a local valid NTS")
        return Observable.just(null)
    }

    fun shouldUpdateData(): Boolean {
        val nowInMilliseconds = Date().time
        val secondsElapsedInMilliseconds = nowInMilliseconds - lastSyncTime.time
        return secondsElapsedInMilliseconds >= THROTTLE_INTERVAL_IN_MILLISECONDS
    }

    private fun storeFeeData(realTimeFees: RealTimeFees) {
        feeWindowRepository.store(realTimeFees.feeWindow)
        val minMempoolFeeRateInSatsPerWeightUnit =
            Rules.toSatsPerWeight(realTimeFees.minMempoolFeeRateInSatPerVbyte)
        minFeeRateRepository.store(minMempoolFeeRateInSatsPerWeightUnit)
    }
}