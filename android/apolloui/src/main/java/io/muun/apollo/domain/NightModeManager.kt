package io.muun.apollo.domain

import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.NightMode
import rx.Observable
import rx.subjects.BehaviorSubject
import javax.inject.Inject

class NightModeManager @Inject constructor(private val libwalletClient: LibwalletClient) {

    companion object {
        private const val KEY = "nightMode"
    }

    private var nightModeSubject = BehaviorSubject.create<NightMode>()

    fun watch(): Observable<NightMode> =
        nightModeSubject
            .startWith(get())
            .distinctUntilChanged()

    fun get(): NightMode =
        libwalletClient.getEnum(KEY, NightMode.FOLLOW_SYSTEM)

    fun save(mode: NightMode) {
        libwalletClient.saveEnum(KEY, mode)
        nightModeSubject.onNext(mode)
    }
}