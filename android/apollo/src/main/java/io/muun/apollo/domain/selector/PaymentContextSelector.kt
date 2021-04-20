package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.MinFeeRateRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.model.PaymentContext
import rx.Observable
import javax.inject.Inject


class PaymentContextSelector @Inject constructor(
    private val userSel: UserSelector,
    private val feeWindowRepository: FeeWindowRepository,
    private val exchangeRateWindowRepository: ExchangeRateWindowRepository,
    private val transactionSizeRepository: TransactionSizeRepository,
    private val minFeeRateRepository: MinFeeRateRepository
) {

    fun watch(): Observable<PaymentContext> =
        Observable.combineLatest(
            userSel.watch(),
            exchangeRateWindowRepository.fetch(),
            feeWindowRepository.fetch(),
            transactionSizeRepository.watchNextTransactionSize(),
            minFeeRateRepository.fetch(),
            ::PaymentContext
        )
}