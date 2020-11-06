package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.common.model.ExchangeRateProvider
import rx.Observable
import javax.inject.Inject

class ExchangeRateSelector @Inject constructor(
    private val exchangeRateWindowRepository: ExchangeRateWindowRepository
) {

    fun watch(): Observable<ExchangeRateProvider> =
        exchangeRateWindowRepository.fetch()
            .map { rateWindow ->
                ExchangeRateProvider(rateWindow.rates)
            }
}