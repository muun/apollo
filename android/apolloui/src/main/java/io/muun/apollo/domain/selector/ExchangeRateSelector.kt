package io.muun.apollo.domain.selector

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.toApolloModel
import io.muun.apollo.domain.errors.InvalidExchangeRateWindow
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.common.model.ExchangeRateProvider
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

class ExchangeRateSelector @Inject constructor(
    private val exchangeRateWindowRepository: ExchangeRateWindowRepository
) {

    fun watchLatest(): Observable<ExchangeRateProvider> =
        exchangeRateWindowRepository.fetch()
            .map { rateWindow ->
                return@map ExchangeRateProvider(rateWindow.toJson())
            }

    fun watchLatestWindow(): Observable<ExchangeRateWindow> =
        exchangeRateWindowRepository.fetch()

    fun getProviderForWindow(rateWindowId: Long): ExchangeRateProvider {

        val latestProvider = watchLatest().toBlocking().first()
        val latestWindowId = checkNotNull(latestProvider.rateWindow?.id)

        val maybeFixedWindow = exchangeRateWindowRepository.fixedWindow
        val fixedWindowId = maybeFixedWindow.map(ExchangeRateWindow::windowHid).orElse(null)
        val matchesFixedWindowId = maybeFixedWindow.isPresent && fixedWindowId == rateWindowId

        return if (latestWindowId == rateWindowId) {
            latestProvider

        } else if (matchesFixedWindowId && !isTooOld(maybeFixedWindow.get(), latestProvider)) {
            ExchangeRateProvider(maybeFixedWindow.get().toJson())

        } else {

            // Let's try to detect errors early in dev but avoid interrupting execution in prd
            val error = InvalidExchangeRateWindow(rateWindowId, latestWindowId, fixedWindowId)

            // TODO add prd check too?
            if (!Globals.INSTANCE.isRelease) {
                throw error
            }

            Timber.e(error)
            latestProvider
        }
    }

    // TODO: this method should be removed, instead using ExchangeRateProvider everywhere
    fun getLatestWindow(): ExchangeRateWindow =
        watchLatestWindow().toBlocking().first()

    fun fixWindow(exchangeRateWindow: ExchangeRateWindow) =
        exchangeRateWindowRepository.storeAndFix(exchangeRateWindow)

    private fun isTooOld(rateWindow: ExchangeRateWindow, latest: ExchangeRateProvider): Boolean {
        // Instead of checking the rates against the current time, we check it against the time
        // of the last successful fetch of the rates, so that we degrade a little bit more
        // gracefully if the exchange rates API is down.
        val latestDate = latest.rateWindow!!.fetchDate
        val lowerLimit = latestDate.toApolloModel()!!.minusHours(1)
        return rateWindow.fetchDate <= lowerLimit
    }
}