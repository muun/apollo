package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.PaymentContext
import io.muun.common.model.SizeForAmount
import rx.Observable
import javax.inject.Inject


class PaymentContextSelector @Inject constructor(
    private val userRepository: UserRepository,
    private val feeWindowRepository: FeeWindowRepository,
    private val exchangeRateWindowRepository: ExchangeRateWindowRepository,
    private val transactionSizeRepository: TransactionSizeRepository,
    private val hardwareWalletStateSelector: HardwareWalletStateSelector
) {

    fun watch(operationUri: OperationUri? = null) = Observable.combineLatest(
        userRepository.fetch(),
        exchangeRateWindowRepository.fetch(),
        feeWindowRepository.fetch(),
        getSizeProgression(operationUri),
        ::PaymentContext
    )

    // This is a workaround for Java's inability to call constructor with optional params
    // TODO: kotlinize caller class and remove this
    fun watch() =
        watch(null)

    private fun getSizeProgression(opUri: OperationUri? = null): Observable<List<SizeForAmount>> {
        if (opUri != null && opUri.isWithdrawal) {

            return hardwareWalletStateSelector.watch().map { walletStateByHid ->
                val hardwareWalletState = walletStateByHid[opUri.hardwareWalletHid]
                checkNotNull(hardwareWalletState)
                checkNotNull(hardwareWalletState.sizeForAmounts)
                hardwareWalletState.sizeForAmounts
            }

        } else {
            return transactionSizeRepository.watchNextTransactionSize()
                    .map { it.sizeProgression }
        }
    }

    fun get(operationUri: OperationUri? = null) =
        watch(operationUri).toBlocking().first()

}