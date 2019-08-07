package io.muun.apollo.domain.selector

import android.annotation.SuppressLint
import io.muun.common.crypto.hwallet.HardwareWalletState
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.*
import javax.inject.Inject

class HardwareWalletStateSelector @Inject constructor() {

    fun watch(): Observable<Map<Long, HardwareWalletState>> =
            walletStateByHid

    fun get(): Map<Long, HardwareWalletState> =
            watch().toBlocking().first()

    companion object {

        @SuppressLint("UseSparseArrays")
        val walletStateByHid: BehaviorSubject<Map<Long, HardwareWalletState>>
                = BehaviorSubject.create(HashMap())

        fun putInCache(hid: Long, state: HardwareWalletState) {
            walletStateByHid.onNext(mapOf(hid to state))
        }
    }
}

