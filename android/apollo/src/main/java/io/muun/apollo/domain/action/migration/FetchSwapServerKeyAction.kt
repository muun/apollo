package io.muun.apollo.domain.action.migration

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.utils.toVoid
import rx.Observable
import javax.inject.Inject

class FetchSwapServerKeyAction @Inject constructor(
    val keysRepository: KeysRepository,
    val houstonClient: HoustonClient,
) : BaseAsyncAction0<Void>() {

    override fun action(): Observable<Void> {
        return Observable.defer {
            val publicKey = keysRepository.basePublicKey

            houstonClient.updatePublicKeySet(publicKey).map {
                val swapServerKey = it.basePublicKeyTriple.swapServerPublicKey
                keysRepository.storeSwapServerPublicKey(swapServerKey)
            }.toVoid()
        }
    }
}
