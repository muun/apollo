package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.BitcoinUnit
import rx.Observable
import javax.inject.Inject


class BitcoinUnitSelector @Inject constructor(private val userRepository: UserRepository) {

    fun watch(): Observable<BitcoinUnit> =
        userRepository.watchBitcoinUnit()

    fun get(): BitcoinUnit =
        watch().toBlocking().first()

}