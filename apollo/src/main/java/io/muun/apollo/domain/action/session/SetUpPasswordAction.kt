package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.keys.CreateChallengeSetupAction
import io.muun.apollo.domain.action.keys.StoreChallengeKeyAction
import io.muun.apollo.domain.utils.flatDoOnNext
import io.muun.apollo.domain.utils.zipWith
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.ChallengeSignature
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetUpPasswordAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val keysRepository: KeysRepository,
    private val createChallengeSetup: CreateChallengeSetupAction,
    private val storeChallengeKey: StoreChallengeKeyAction

): BaseAsyncAction1<String, Void>() {

    /**
     * Creates a new user with their first session in Houston.
     */
    override fun action(password: String) =
        keysRepository.anonSecret
            .zipWith(
                houstonClient.requestChallenge(ChallengeType.ANON)
            )
            .flatMap { (anonSecret, maybeChallenge) ->
                val challenge = maybeChallenge.orElseThrow() // empty only for legacy apps

                val signatureBytes = ChallengePrivateKey
                    .fromUserInput(anonSecret, challenge.salt)
                    .sign(challenge.challenge)

                val chSig = ChallengeSignature(ChallengeType.ANON, signatureBytes)


                createChallengeSetup.action(ChallengeType.PASSWORD, password)
                    .flatMap { chSetup ->
                        houstonClient
                            .setUpPassword(chSig, chSetup)
                            .flatDoOnNext {
                                storeChallengeKey.action(chSetup.type, chSetup.publicKey)
                            }
                    }
            }
}
