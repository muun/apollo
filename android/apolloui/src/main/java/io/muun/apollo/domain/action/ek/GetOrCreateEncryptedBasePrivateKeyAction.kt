package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.utils.Encodings
import io.muun.common.utils.Preconditions
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetOrCreateEncryptedBasePrivateKeyAction @Inject constructor(
    private val keysRepository: KeysRepository
): BaseAsyncAction0<String>() {
    /**
     * Prepare the emergency kit for export, and render the HTML.
     */
    override fun action(): Observable<String> {
        if (keysRepository.hasEncryptedBasePrivateKey) {
            return keysRepository.encryptedBasePrivateKey
        }

        Preconditions.checkState(keysRepository.hasChallengePublicKey(ChallengeType.RECOVERY_CODE))
        Preconditions.checkState(keysRepository.hasEncryptedMuunPrivateKey)
        Preconditions.checkState(keysRepository.hasBasePrivateKey)

        val encryptedMuunPrivateKey = keysRepository.encryptedMuunPrivateKey.toBlocking().first()
        val basePrivateKey = keysRepository.basePrivateKey.toBlocking().first()

        return keysRepository.getChallengePublicKey(ChallengeType.RECOVERY_CODE)
            .map { challengePublicKey: ChallengePublicKey ->
                val birthday: Long = 0

                challengePublicKey.encryptPrivateKey(
                    encryptedMuunPrivateKey,
                    basePrivateKey,
                    birthday
                )
            }
            .flatMap { encryptedKey: String ->
                Preconditions.checkNotNull(encryptedKey)
                Timber.d("Storing encrypted Apollo private key in secure storage.")
                keysRepository.storeUserKeyFingerprint(Encodings.bytesToHex(
                    basePrivateKey.fingerprint //TODO: why is this set coupled to the encryptedBasePrivateKey?
                ))

                keysRepository.storeEncryptedBasePrivateKey(encryptedKey)

                Observable.just(encryptedKey)
            }
    }
}