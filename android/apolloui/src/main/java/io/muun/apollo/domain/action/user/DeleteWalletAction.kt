package io.muun.apollo.domain.action.user

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.domain.action.LogoutActions
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.action.challenge_keys.SignChallengeAction
import io.muun.apollo.domain.selector.UserSelector
import io.muun.common.Optional
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.Challenge
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteWalletAction @Inject constructor(
    private val userSel: UserSelector,
    private val logoutActions: LogoutActions,
    private val signChallenge: SignChallengeAction,
    private val houstonClient: HoustonClient,
) : BaseAsyncAction0<Optional<String>>() {

    override fun action(): Observable<Optional<String>> {
        return Observable.defer {
            houstonClient.requestChallenge(ChallengeType.USER_KEY)
                .map { maybeChallenge: Optional<Challenge> ->
                    val challenge = maybeChallenge.orElseThrow() // empty only for legacy apps
                    signChallenge.signWithUserKey(challenge)
                }
                .flatMap { challengeSignature ->
                    houstonClient.deleteWallet(challengeSignature)
                        .andThen(Observable.just(null))
                }
                .map {
                    val maybeSupportId = userSel.getOptional().flatMap { it.supportId }
                    logoutActions.dangerouslyDestroyWallet()
                    maybeSupportId
                }
        }
    }
}