package io.muun.apollo.domain.action.challenge_keys.recovery_code_setup

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.challenge_keys.CreateChallengeSetupAction
import io.muun.apollo.domain.action.challenge_keys.StoreUnverifiedRcChallengeKeyAction
import io.muun.apollo.domain.errors.rc.StartRecoveryCodeSetupError
import io.muun.apollo.domain.libwallet.RecoveryCodeV2
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.api.SetupChallengeResponse
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.ChallengeSetup
import io.muun.common.utils.Preconditions
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartRecoveryCodeSetupAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val createChallengeSetup: CreateChallengeSetupAction,
    private val storeUnverifiedRcChallengeKey: StoreUnverifiedRcChallengeKeyAction,
    private val keysRepository: KeysRepository,
) : BaseAsyncAction1<RecoveryCodeV2, Void>() {

    /**
     * Start the 2-step setup process of a RECOVERY CODE Challenge Key in Houston.
     */
    override fun action(recoveryCode: RecoveryCodeV2): Observable<Void> {
        return startRecoveryCodeSetup(recoveryCode.toString())
            .doOnNext { setupChallengeResponse ->
                // MuunEncryptedKey is always returned after a successful RC challenge setup
                Preconditions.checkNotNull(setupChallengeResponse.muunKey)
                Preconditions.checkNotNull(setupChallengeResponse.muunKeyFingerprint)

                keysRepository.storeEncryptedMuunPrivateKey(setupChallengeResponse.muunKey!!)
                keysRepository.storeMuunKeyFingerprint(setupChallengeResponse.muunKeyFingerprint!!)
            }
            .onErrorResumeNext { error ->
                Observable.error(StartRecoveryCodeSetupError(error))
            }
            .toVoid()
    }

    private fun startRecoveryCodeSetup(recoveryCode: String): Observable<SetupChallengeResponse> {
        return createChallengeSetup.action(ChallengeType.RECOVERY_CODE, recoveryCode)
            .flatMap { chSetup: ChallengeSetup ->
                houstonClient.startChallengeSetup(chSetup)
                    .flatMap { setupChallengeResponse ->
                        storeUnverifiedRcChallengeKey.action(chSetup.publicKey)
                            .map { setupChallengeResponse }
                    }
            }
    }
}