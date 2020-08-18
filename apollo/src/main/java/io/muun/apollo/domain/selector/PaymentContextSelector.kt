package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.PaymentContext
import rx.Observable
import javax.inject.Inject


class PaymentContextSelector @Inject constructor(
    private val userSel: UserSelector,
    private val feeWindowRepository: FeeWindowRepository,
    private val exchangeRateWindowRepository: ExchangeRateWindowRepository,
    private val transactionSizeRepository: TransactionSizeRepository,
    private val hardwareWalletStateSelector: HardwareWalletStateSelector
) {

    fun watch(operationUri: OperationUri? = null) = Observable.combineLatest(
        userSel.watch(),
        exchangeRateWindowRepository.fetch(),
        feeWindowRepository.fetch(),
        watchtNextTransactionSize(operationUri),
        ::PaymentContext
    )

    // This is a workaround for Java's inability to call constructor with optional params
    // TODO: kotlinize caller class and remove this
    fun watch() =
        watch(null)

    fun get(operationUri: OperationUri? = null) =
        watch(operationUri).toBlocking().first()

    private fun watchtNextTransactionSize(opUri: OperationUri? = null) =
        if (opUri != null && opUri.isWithdrawal) {
            watchHardwareWalletNextTransactionSize(opUri.hardwareWalletHid)
        } else {
            transactionSizeRepository.watchNextTransactionSize()
        }

    private fun watchHardwareWalletNextTransactionSize(hardwareWalletHid: Long) =
        hardwareWalletStateSelector.watch()
            .map { walletStateByHid ->
                val hardwareWalletState = walletStateByHid[hardwareWalletHid]
                checkNotNull(hardwareWalletState)
                checkNotNull(hardwareWalletState.sizeForAmounts)

                NextTransactionSize(hardwareWalletState.sizeForAmounts, 0L, 0L)
            }

}