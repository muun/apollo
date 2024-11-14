package io.muun.apollo.domain.action.realtime

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.MinFeeRateRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.errors.FeeBumpFunctionsStoreError
import io.muun.apollo.domain.model.RealTimeFees
import io.muun.apollo.domain.utils.toLibwalletModel
import io.muun.common.Rules
import io.muun.common.rx.RxHelper
import newop.Newop
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Update fees data, such as fee window and fee bump functions.
 */

@Singleton
class FetchRealTimeFeesAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val feeWindowRepository: FeeWindowRepository,
    private val minFeeRateRepository: MinFeeRateRepository,
    private val transactionSizeRepository: TransactionSizeRepository
) : BaseAsyncAction0<Void>() {
    override fun action(): Observable<Void> {
        return Observable.defer {
            syncRealTimeFees()
        }
    }

    private fun syncRealTimeFees(): Observable<Void> {
        Timber.d("[Sync] Updating realTime fees data")

        transactionSizeRepository.nextTransactionSize?.sizeProgression?.let {
            return houstonClient.fetchRealTimeFees(it)
                .doOnNext { realTimeFees: RealTimeFees ->
                    Timber.d("[Sync] Saving updated fees")
                    storeFeeData(realTimeFees)
                    storeFeeBumpFunctions(realTimeFees.feeBumpFunctions)
                }
                .map(RxHelper::toVoid)
        }

        Timber.e("syncRealTimeFees was called without a local valid NTS")
        return Observable.just(null)
    }

    private fun storeFeeData(realTimeFees: RealTimeFees) {
        feeWindowRepository.store(realTimeFees.feeWindow)
        val minMempoolFeeRateInSatsPerWeightUnit =
            Rules.toSatsPerWeight(realTimeFees.minMempoolFeeRateInSatPerVbyte)
        minFeeRateRepository.store(minMempoolFeeRateInSatsPerWeightUnit)
    }

    private fun storeFeeBumpFunctions(feeBumpFunctions: List<String>) {
        val feeBumpFunctionsStringList = feeBumpFunctions.toLibwalletModel()

        try {
            Newop.persistFeeBumpFunctions(feeBumpFunctionsStringList)
        } catch (e: Exception) {
            Timber.e(e, "Error storing fee bump functions")
            throw FeeBumpFunctionsStoreError(feeBumpFunctions.toString(), e)
        }
    }
}