package io.muun.apollo.domain.action.keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.challenge_keys.StoreChallengeKeyAction
import io.muun.apollo.domain.errors.PasswordIntegrityError
import io.muun.common.api.KeySet
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.hd.KeyCrypter
import io.muun.common.utils.Encodings
import io.muun.common.utils.Preconditions
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecryptAndStoreKeySetAction @Inject constructor(
    private val storeChallengeKey: StoreChallengeKeyAction,
    private val keysRepository: KeysRepository
): BaseAsyncAction2<KeySet, String, Void>() {

    override fun action(keyset: KeySet, userInput: String): Observable<Void> =
        Observable.defer { decryptAndStore(keyset, userInput) }

    private fun decryptAndStore(keySet: KeySet, userInput: String): Observable<Void> {

        if (keySet.challengeKeys != null) {
            for (challengeKey in keySet.challengeKeys!!) {

                val publicKey = ChallengePublicKey(
                    Encodings.hexToBytes(challengeKey.publicKey),
                    Encodings.hexToBytes(challengeKey.salt!!), // Never null at this point
                    challengeKey.challengeVersion
                )

                storeChallengeKey.actionNow(challengeKey.type, publicKey)
            }
        }

        if (keySet.muunKey != null) {
            Preconditions.checkNotNull(keySet.muunKeyFingerprint)

            keysRepository.storeEncryptedMuunPrivateKey(keySet.muunKey)
            keysRepository.storeMuunKeyFingerprint(keySet.muunKeyFingerprint)
        }

        val userPrivateKey = KeyCrypter().decrypt(keySet.encryptedPrivateKey, userInput)
            .orElseThrow { PasswordIntegrityError() }

        return keysRepository.storeBasePrivateKey(userPrivateKey)
    }
}