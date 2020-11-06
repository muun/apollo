package io.muun.apollo.domain.action.challenge_keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.KeyCrypter
import io.muun.common.model.challenge.ChallengeSetup
import io.muun.common.utils.Encodings
import io.muun.common.utils.RandomGenerator
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject

open class CreateChallengeSetupAction @Inject constructor(
    private val keysRepository: KeysRepository
) : BaseAsyncAction2<ChallengeType, String, ChallengeSetup>() {

    /**
     * Create a ChallengeSetup generating a salt, a key and encrypting the base private key.
     */
    override fun action(type: ChallengeType, secret: String): Observable<ChallengeSetup> =
        Observable.defer {
            buildSetup(type, secret)
        }

    private fun buildSetup(type: ChallengeType, secret: String): Observable<ChallengeSetup> {
        val salt = generateSecureSalt()
        val publicKey = buildChallengePublicKey(type, secret, salt)

        return Observable
            .defer {
                if (type.encryptsPrivateKey) {
                    keysRepository.basePrivateKey.map { KeyCrypter().encrypt(it, secret) }
                } else {
                    Observable.just(null as String?)
                }
            }
            .map { encryptedPrivateKeyOrNull ->
                ChallengeSetup(type, publicKey, salt, encryptedPrivateKeyOrNull, publicKey.version)
            }
    }

    private fun buildChallengePublicKey(type: ChallengeType, secret: String, salt: ByteArray) =
        if (type == ChallengeType.RECOVERY_CODE) {

            val challengePrivateKey = Libwallet.recoveryCodeToKey(secret, null)
            val pubKeyBytes = Encodings.hexToBytes(challengePrivateKey.pubKeyHex())

            ChallengePublicKey(pubKeyBytes, salt, 2)

        } else {
            ChallengePrivateKey.fromUserInput(secret, salt, 1).challengePublicKey
        }

    private fun generateSecureSalt() =
        RandomGenerator.getBytes(8)
}