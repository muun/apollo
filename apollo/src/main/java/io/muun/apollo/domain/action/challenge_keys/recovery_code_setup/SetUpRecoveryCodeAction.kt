package io.muun.apollo.domain.action.challenge_keys.recovery_code_setup

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.challenge_keys.SetUpChallengeKeyAction
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.crypto.ChallengeType
import io.muun.common.utils.Preconditions
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetUpRecoveryCodeAction @Inject constructor(
    private val setUpChallengeKey: SetUpChallengeKeyAction,
    private val keysRepository: KeysRepository,
    private val userRepository: UserRepository
) : BaseAsyncAction1<String, Void>() {

    /**
     * Set up a RECOVERY CODE Challenge Key in Houston.
     */
    override fun action(recoveryCode: String): Observable<Void> {
        return setUpChallengeKey.action(ChallengeType.RECOVERY_CODE, recoveryCode)
            .doOnNext { setupChallengeResponse ->

                // MuunEncriptedKey is always returned after a successful RC challenge setup
                Preconditions.checkNotNull(setupChallengeResponse.muunKey)
                Preconditions.checkNotNull(setupChallengeResponse.muunKeyFingerprint)

                keysRepository.storeEncryptedMuunPrivateKey(setupChallengeResponse.muunKey)
                keysRepository.storeMuunKeyFingerprint(setupChallengeResponse.muunKeyFingerprint)

                userRepository.setRecoveryCodeSetupInProcess(false)
            }
            .toVoid()
    }
}
