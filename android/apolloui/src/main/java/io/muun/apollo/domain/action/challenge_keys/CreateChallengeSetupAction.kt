package io.muun.apollo.domain.action.challenge_keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.libwallet.toLibwallet
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.model.challenge.ChallengeSetup
import io.muun.common.utils.RandomGenerator
import libwallet.Libwallet
import org.bitcoinj.core.NetworkParameters
import rx.Observable
import javax.inject.Inject

open class CreateChallengeSetupAction @Inject constructor(
    private val keysRepository: KeysRepository,
    private val network: NetworkParameters,
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
        val publicKey = ChallengeKey.buildPublic(type, secret, salt)

        return Observable
            .defer {
                if (type.encryptsPrivateKey) {
                    keysRepository.basePrivateKey.map { keyEncrypt(it, secret) }
                } else {
                    Observable.just(null as String?)
                }
            }
            .map { encryptedPrivateKeyOrNull ->
                ChallengeSetup(type, publicKey, salt, encryptedPrivateKeyOrNull, publicKey.version)
            }
    }

    private fun keyEncrypt(privateKey: PrivateKey, secret: String): String? {
        return Libwallet.keyEncrypt(privateKey.toLibwallet(network), secret)
    }

    private fun generateSecureSalt() =
        RandomGenerator.getBytes(8)
}