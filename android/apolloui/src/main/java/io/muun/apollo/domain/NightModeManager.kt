package io.muun.apollo.domain

import io.muun.apollo.domain.libwallet.WalletClient
import io.muun.apollo.domain.model.NightMode
import rx.Observable
import rx.subjects.BehaviorSubject
import javax.inject.Inject

class NightModeManager @Inject constructor(private val walletClient: WalletClient) {

    companion object {
        private const val KEY = "nightMode"
    }

    private var nightModeSubject = BehaviorSubject.create<NightMode>()

    fun watch(): Observable<NightMode> =
        nightModeSubject
            .startWith(get())
            .distinctUntilChanged()

    fun get(): NightMode =
        walletClient.getEnum(KEY, NightMode.FOLLOW_SYSTEM)

    fun save(mode: NightMode) {
        walletClient.saveEnum(KEY, mode)
        nightModeSubject.onNext(mode)
    }
}