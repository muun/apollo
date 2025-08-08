package io.muun.apollo.domain.action.challenge_keys.password_change

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.challenge_keys.CreateChallengeSetupAction
import io.muun.apollo.domain.action.challenge_keys.StoreVerifiedChallengeKeyAction
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.ChallengeSetup
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinishPasswordChangeAction @Inject constructor (
    private val houstonClient: HoustonClient,
    private val createChallengeSetup: CreateChallengeSetupAction,
    private val storeChallengeKey: StoreVerifiedChallengeKeyAction,
) : BaseAsyncAction2<String, String, Void>() {

    /**
     * Finish a password change process by submitting a new ChallengeSetup, built with the
     * new password, and a process' identifying uuid.
     */
    override fun action(uuid: String, newPassword: String): Observable<Void> {
        return createChallengeSetup.action(ChallengeType.PASSWORD, newPassword)
            .flatMap { challengeSetup: ChallengeSetup ->
                houstonClient.finishPasswordChange(uuid, challengeSetup)
                    .doOnNext {
                        storeChallengeKey.run(
                            ChallengeType.PASSWORD,
                            challengeSetup.publicKey,
                            )
                    }
            }
    }
}