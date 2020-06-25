package io.muun.apollo.domain.action.keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.KeyCrypter
import io.muun.common.model.challenge.ChallengeSetup
import io.muun.common.utils.RandomGenerator
import rx.Observable
import javax.inject.Inject

open class CreateChallengeSetupAction @Inject constructor(
    private val keysRepository: KeysRepository

): BaseAsyncAction2<ChallengeType, String, ChallengeSetup>() {

    /**
     * Create a ChallengeSetup generating a salt, a key and encrypting the base private key.
     */
    override fun action(type: ChallengeType, secret: String) =
        Observable.defer {
            buildSetup(type, secret)
        }

    private fun buildSetup(type: ChallengeType, secret: String): Observable<ChallengeSetup> {
        val version = ChallengeType.getVersion(type)
        val salt = generateSecureSalt()
        val publicKey = ChallengePrivateKey.fromUserInput(secret, salt).challengePublicKey

        return Observable
            .defer {
                if (type.encryptsPrivateKey) {
                    keysRepository.basePrivateKey.map { KeyCrypter().encrypt(it, secret) }
                } else {
                    Observable.just(null as String?)
                }
            }
            .map { encryptedPrivateKeyOrNull ->
                ChallengeSetup(type, publicKey, salt, encryptedPrivateKeyOrNull, version)
            }
    }

    private fun generateSecureSalt() =
        RandomGenerator.getBytes(8)
}