package io.muun.apollo.domain.action.session

import io.muun.apollo.data.async.gcm.FirebaseManager
import io.muun.apollo.data.logging.LoggingContext
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.CurrencyActions
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.keys.CreateRootPrivateKeyAction
import io.muun.apollo.domain.errors.FcmTokenNotAvailableError
import io.muun.apollo.domain.model.CreateFirstSessionOk
import io.muun.apollo.domain.utils.replaceTypedError
import io.muun.apollo.domain.utils.zipWith
import io.muun.apollo.external.Globals
import io.muun.common.Optional
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.CreateSessionOk
import io.muun.common.model.challenge.Challenge
import io.muun.common.model.challenge.ChallengeSetup
import io.muun.common.model.challenge.ChallengeSignature
import io.muun.common.utils.Encodings
import io.muun.common.utils.RandomGenerator
import rx.Observable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import javax.validation.constraints.NotNull

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
