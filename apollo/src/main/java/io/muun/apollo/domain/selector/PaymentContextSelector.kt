package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.PaymentContext
import rx.Observable
import javax.inject.Inject


class PaymentContextSelector @Inject constructor(
    private val userRepository: UserRepository,
    private val feeWindowRepository: FeeWindowRepository,
    private val exchangeRateWindowRepository: ExchangeRateWindowRepository,
    private val transactionSizeRepository: TransactionSizeRepository
) {

    fun watch() = Observable.combineLatest(
        userRepository.fetch(),
        exchangeRateWindowRepository.fetch(),
        feeWindowRepository.fetch(),
        transactionSizeRepository.watchNextTransactionSize(),
        ::PaymentContext
    )

    fun get() =
        watch().toBlocking().first()

}