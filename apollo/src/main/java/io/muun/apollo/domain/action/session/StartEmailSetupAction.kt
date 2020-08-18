package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.utils.zipWith
import io.muun.common.Optional
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.ChallengeSignature
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartEmailSetupAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val keysRepository: KeysRepository,
    private val userRepository: UserRepository

): BaseAsyncAction1<String, Void>() {

    /**
     * Begin the email setup process, by signing a challenge en requesting a verification email.
     */
    override fun action(email: String) =
        Observable.defer {
            keysRepository.anonSecret
                .zipWith(
                    houstonClient.requestChallenge(ChallengeType.ANON)
                )
                .map { (anonSecret, maybeChallenge) ->
                    val challenge = maybeChallenge.orElseThrow() // empty only for legacy apps

                    val signatureBytes = ChallengePrivateKey
                        .fromUserInput(anonSecret, challenge.salt)
                        .sign(challenge.challenge)

                    ChallengeSignature(challenge.type, signatureBytes)
                }
                .flatMap {
                    houstonClient.startEmailSetup(email, it)
                }
                .doOnNext {
                    val user = userRepository.fetchOne()
                    user.email = Optional.of(email)
                    user.isEmailVerified = false

                    userRepository.store(user)
                }
        }
}
