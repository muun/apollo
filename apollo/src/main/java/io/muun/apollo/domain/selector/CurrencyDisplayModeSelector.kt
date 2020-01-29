package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.CurrencyDisplayMode
import rx.Observable
import javax.inject.Inject


class CurrencyDisplayModeSelector @Inject constructor(
    private val userRepository: UserRepository
) {

    fun watch(): Observable<CurrencyDisplayMode> =
        userRepository.watchCurrencyDisplayMode()

    fun get(): CurrencyDisplayMode =
        watch().toBlocking().first()

}