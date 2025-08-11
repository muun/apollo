package io.muun.apollo.domain.action.challenge_keys.recovery_code_setup

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.errors.rc.FinishRecoveryCodeSetupError
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import rx.Observable
import javax.inject.Inject

class FinishRecoveryCodeSetupAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val keysRepository: KeysRepository,
    private val userRepository: UserRepository,
) : BaseAsyncAction0<ChallengePublicKey>() {

    /**
     * Finish/Verify the setup of a RECOVERY CODE Challenge Key in Houston.
     */
    override fun action(): Observable<ChallengePublicKey> {
        return keysRepository.getChallengePublicKey(ChallengeType.RECOVERY_CODE)
            .flatMap { challengePublicKey ->
                houstonClient.finishChallengeSetup(ChallengeType.RECOVERY_CODE, challengePublicKey)
                    .andThen(Observable.fromCallable {
                        userRepository.setRecoveryCodeSetupInProcess(false)
                        userRepository.setHasRecoveryCode()
                        return@fromCallable challengePublicKey
                    })
            }
            .onErrorResumeNext { error ->
                Observable.error(FinishRecoveryCodeSetupError(error))
            }
    }
}