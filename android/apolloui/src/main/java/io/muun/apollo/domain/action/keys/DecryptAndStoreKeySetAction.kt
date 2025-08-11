package io.muun.apollo.domain.action.keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.challenge_keys.StoreVerifiedChallengeKeyAction
import io.muun.apollo.domain.errors.passwd.PasswordIntegrityError
import io.muun.apollo.domain.libwallet.Extensions
import io.muun.apollo.domain.libwallet.toLibwallet
import io.muun.common.api.KeySet
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.utils.Encodings
import io.muun.common.utils.Preconditions
import libwallet.Libwallet
import org.bitcoinj.core.NetworkParameters
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecryptAndStoreKeySetAction @Inject constructor(
    private val storeChallengeKey: StoreVerifiedChallengeKeyAction,
    private val keysRepository: KeysRepository,
    private val network: NetworkParameters,
) : BaseAsyncAction2<KeySet, String, Void>() {

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

            keysRepository.storeEncryptedMuunPrivateKey(keySet.muunKey!!)
            keysRepository.storeMuunKeyFingerprint(keySet.muunKeyFingerprint!!)
        }

        try {
            val decryptedKey = Libwallet.keyDecrypt(
                keySet.encryptedPrivateKey,
                userInput,
                network.toLibwallet()
            )
            val userPrivateKey = Extensions.fromLibwallet(decryptedKey.key)

            return keysRepository.storeBasePrivateKey(userPrivateKey)
        } catch (e: Exception) {
            throw PasswordIntegrityError()
        }
    }
}