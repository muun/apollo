package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.SigninActions
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.keys.DecryptRootPrivateKeyAction
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError
import io.muun.apollo.domain.errors.PasswordIntegrityError
import io.muun.apollo.domain.utils.replaceTypedError
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.api.KeySet
import io.muun.common.api.SetupChallengeResponse
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.Challenge
import io.muun.common.model.challenge.ChallengeSignature
import io.muun.common.utils.Encodings
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LogInAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val signinActions: SigninActions,
    private val userActions: UserActions,
    private val keysRepository: KeysRepository,
    private val decryptRootPrivateKey: DecryptRootPrivateKeyAction

): BaseAsyncAction2<ChallengeType, String, Void>() {

    override fun action(challengeType: ChallengeType, userInput: String) =
        Observable.defer { login(challengeType, userInput) }

    /**
     * Login with a challenge, or in compatibility mode and set one up.
     */
    private fun login(challengeType: ChallengeType, userInput: String): Observable<Void> =
        houstonClient.requestChallenge(challengeType)
            .flatMap {
                if (it.isPresent) {
                    loginWithChallenge(it.get(), userInput)
                } else {
                    loginCompatWithoutChallenge(userInput)
                }
            }

    private fun loginWithChallenge(challenge: Challenge, userInput: String) =
        houstonClient
            .login(signChallenge(challenge, userInput))
            .flatMap { keySet -> decryptStoreKeySet(keySet, userInput) }
            .toVoid()


    private fun loginCompatWithoutChallenge(password: String) =
        houstonClient
            .loginCompatWithoutChallenge()
            .flatMap {
                keySet -> decryptStoreKeySet(keySet, password)
            }
            .replaceTypedError(PasswordIntegrityError::class.java) {
                // Without challenges, a decryption error is not necessarily an integrity
                // error. Much more likely, the user entered the wrong password.
                // We'll fake a wrong challenge signature.
                InvalidChallengeSignatureError()
            }
            .flatMap<SetupChallengeResponse> {
                signinActions.setupChallenge(ChallengeType.PASSWORD, password)
            }
            .toVoid()


    private fun signChallenge(challenge: Challenge, userInput: String): ChallengeSignature {
        val signatureBytes = ChallengePrivateKey
            .fromUserInput(userInput, challenge.salt)
            .sign(challenge.challenge)

        return ChallengeSignature(challenge.type, signatureBytes)
    }


    private fun decryptStoreKeySet(keySet: KeySet, userInput: String): Observable<Void> {

        if (keySet.challengeKeys != null) {
            for (challengeKey in keySet.challengeKeys!!) {

                val publicKey = ChallengePublicKey(
                    Encodings.hexToBytes(challengeKey.publicKey),
                    Encodings.hexToBytes(challengeKey.salt)
                )

                userActions.storeChallengeKey(challengeKey.type, publicKey)
            }
        }

        if (keySet.muunKey != null) {
            keysRepository.storeEncryptedMuunPrivateKey(keySet.muunKey!!)
        }

        return decryptRootPrivateKey.action(
            keySet.encryptedPrivateKey,
            userInput
        )
    }
}
