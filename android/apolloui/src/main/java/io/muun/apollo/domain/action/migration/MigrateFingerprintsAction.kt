package io.muun.apollo.domain.action.migration

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.utils.Encodings
import rx.Observable
import javax.inject.Inject

class MigrateFingerprintsAction @Inject constructor(
    val keysRepository: KeysRepository,
    val houstonClient: HoustonClient,
) : BaseAsyncAction0<Void>() {

    override fun action(): Observable<Void> =
        Observable.zip(
            getUserKeyFingerprint(),
            getMuunKeyFingerprint(),
            ::Pair
        )
            .doOnNext { (userKeyFingerprint, muunKeyFingerprint) ->
                keysRepository.storeUserKeyFingerprint(userKeyFingerprint!!)
                keysRepository.storeMuunKeyFingerprint(muunKeyFingerprint!!)
            }
            .toVoid()

    private fun getMuunKeyFingerprint(): Observable<String> =
        houstonClient.fetchMuunKeyFingerprint()

    private fun getUserKeyFingerprint(): Observable<String> =
        keysRepository.basePrivateKey
            .map { Encodings.bytesToHex(it.fingerprint) }

}