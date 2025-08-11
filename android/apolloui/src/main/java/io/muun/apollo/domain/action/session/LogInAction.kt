package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.challenge_keys.ChallengeKey
import io.muun.apollo.domain.action.challenge_keys.SetUpChallengeKeyAction
import io.muun.apollo.domain.action.challenge_keys.SignChallengeAction
import io.muun.apollo.domain.action.keys.DecryptAndStoreKeySetAction
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError
import io.muun.apollo.domain.errors.passwd.PasswordIntegrityError
import io.muun.apollo.domain.model.auth.LoginOk
import io.muun.apollo.domain.utils.replaceTypedError
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.Challenge
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LogInAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val setUpChallengeKey: SetUpChallengeKeyAction,
    private val signChallenge: SignChallengeAction,
    private val decryptAndStoreKeySet: DecryptAndStoreKeySetAction,
) : BaseAsyncAction2<ChallengeType, String, LoginOk>() {

    override fun action(challengeType: ChallengeType, userInput: String): Observable<LoginOk> =
        Observable.defer { login(challengeType, userInput) }

    /**
     * Login with a challenge, or in compatibility mode and set one up.
     */
    private fun login(challengeType: ChallengeType, userInput: String): Observable<LoginOk> =
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
            .login(
                signChallenge.sign(userInput, challenge),
                ChallengeKey.buildPublic(challenge.type, userInput, challenge.salt)
            )
            .flatMap { keySet ->
                decryptAndStoreKeySet.action(keySet, userInput)
                    .map { LoginOk(challenge.type, keySet) }
            }

    private fun loginCompatWithoutChallenge(password: String) =
        houstonClient
            .loginCompatWithoutChallenge()
            .flatMap { keySet ->
                decryptAndStoreKeySet.action(keySet, password)
                    // This compat login was only available for users before RC existed
                    .map { LoginOk(ChallengeType.PASSWORD, keySet) }
            }
            .replaceTypedError(PasswordIntegrityError::class.java) {
                // Without challenges, a decryption error is not necessarily an integrity
                // error. Much more likely, the user entered the wrong password.
                // We'll fake a wrong challenge signature.
                InvalidChallengeSignatureError()
            }
            .flatMap { loginOk ->
                setUpChallengeKey.action(ChallengeType.PASSWORD, password)
                    .map { loginOk }
            }

}
