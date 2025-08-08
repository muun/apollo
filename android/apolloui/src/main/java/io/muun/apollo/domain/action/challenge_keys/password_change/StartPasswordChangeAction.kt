package io.muun.apollo.domain.action.challenge_keys.password_change

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.challenge_keys.SignChallengeAction
import io.muun.apollo.domain.model.PendingChallengeUpdate
import io.muun.common.Optional
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.Challenge
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartPasswordChangeAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val signChallenge: SignChallengeAction,
) : BaseAsyncAction2<String, ChallengeType, PendingChallengeUpdate>() {

    /**
     * Starts password change process by requesting a challenge and sending a challenge signature,
     * signed with the current password or recovery code.
     */
    override fun action(secret: String, type: ChallengeType): Observable<PendingChallengeUpdate> {
        return houstonClient.requestChallenge(type)
            .flatMap { maybeChallenge: Optional<Challenge> ->

                val challenge = maybeChallenge.orElseThrow() // empty only for legacy apps

                houstonClient.beginPasswordChange(signChallenge.sign(secret, challenge))
            }
    }
}