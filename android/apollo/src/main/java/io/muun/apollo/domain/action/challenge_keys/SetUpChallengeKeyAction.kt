package io.muun.apollo.domain.action.challenge_keys

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.common.api.SetupChallengeResponse
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.ChallengeSetup
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetUpChallengeKeyAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val createChallengeSetup: CreateChallengeSetupAction,
    private val storeChallengeKey: StoreVerifiedChallengeKeyAction
) : BaseAsyncAction2<ChallengeType, String, SetupChallengeResponse>() {

    /**
     * Set up a challenge key in Houston.
     */
    override fun action(type: ChallengeType, secret: String): Observable<SetupChallengeResponse> {
        return createChallengeSetup.action(type, secret)
            .flatMap { challengeSetup: ChallengeSetup ->
                houstonClient.setupChallenge(challengeSetup)
                    .flatMap { setupChallengeResponse ->
                        storeChallengeKey.action(type, challengeSetup.publicKey)
                            .map { setupChallengeResponse }
                    }
            }
    }
}
