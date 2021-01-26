package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.ExchangeRateWindow
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

    fun watchWindow(): Observable<ExchangeRateWindow> =
        exchangeRateWindowRepository.fetch()

    // TODO: this method should be removed, instead using ExchangeRateProvider everywhere
    fun getWindow(): ExchangeRateWindow =
        watchWindow().toBlocking().first()
}