package io.muun.apollo.domain.action.challenge_keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.libwallet.LibwalletBridge
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.Challenge
import io.muun.common.model.challenge.ChallengeSignature
import io.muun.common.utils.Encodings
import io.muun.common.utils.Preconditions
import libwallet.Libwallet
import org.bitcoinj.core.NetworkParameters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignChallengeAction @Inject constructor(
    private val keysRepository: KeysRepository,
    private val networkParameters: NetworkParameters
) {

    /**
     * Signs a Challenge using USER_KEY ChallengeType which signals that we sign the challenge
     * using User's base PrivateKey instead of our normal ChallengePrivateKey modus operandi.
     */
    fun signWithUserKey(challenge: Challenge): ChallengeSignature {

        Preconditions.checkArgument(challenge.type == ChallengeType.USER_KEY)

        val signatureBytes = LibwalletBridge.sign(
            challenge.challenge,
            keysRepository.basePrivateKey.toBlocking().first(),
            networkParameters
        )

        return ChallengeSignature(ChallengeType.USER_KEY, signatureBytes)
    }

    /**
     * Signs a Challenge using our normal ChallengePrivateKey modus operandi.
     */
    fun sign(userInput: String, challenge: Challenge): ChallengeSignature {

        Preconditions.checkArgument(
            challenge.type in listOf(ChallengeType.PASSWORD, ChallengeType.RECOVERY_CODE)
        )

        return if (challenge.type == ChallengeType.RECOVERY_CODE) {
            signWithRecoveryCode(userInput, challenge)

        } else {
            signWithPassword(userInput, challenge)
        }
    }

    private fun signWithRecoveryCode(code: String, challenge: Challenge): ChallengeSignature {

        val salt = challenge.salt?.let { Encodings.bytesToHex(it) }
        val signature = Libwallet.recoveryCodeToKey(code, salt)
            .signSha(challenge.challenge)

        return ChallengeSignature(challenge.type, signature)
    }

    private fun signWithPassword(userInput: String, challenge: Challenge): ChallengeSignature {
        val challengePrivateKey = ChallengePrivateKey.fromUserInput(
            userInput,
            challenge.salt,
            1
        )

        return ChallengeSignature(challenge.type, challengePrivateKey.sign(challenge.challenge))
    }


}