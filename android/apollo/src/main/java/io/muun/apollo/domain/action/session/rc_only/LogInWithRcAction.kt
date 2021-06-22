package io.muun.apollo.domain.action.session.rc_only

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FirebaseInstalationIdRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.challenge_keys.SignChallengeAction
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.action.keys.DecryptAndStoreKeySetAction
import io.muun.common.Optional
import io.muun.common.api.KeySet
import io.muun.common.model.CreateSessionRcOk
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton
import javax.validation.constraints.NotNull

@Singleton
class LogInWithRcAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val getFcmToken: GetFcmTokenAction,
    private val signChallenge: SignChallengeAction,
    private val decryptAndStoreKeySet: DecryptAndStoreKeySetAction,
    private val firebaseInstalationIdRepository: FirebaseInstalationIdRepository
) : BaseAsyncAction1<String, CreateSessionRcOk>() {

    override fun action(recoveryCode: String): Observable<CreateSessionRcOk> =
        Observable.defer { loginWithRc(recoveryCode) }

    /**
     * Login with Recovery Code.
     * Note: if user has email set, email auth will still be required.
     */
    private fun loginWithRc(recoveryCode: String): Observable<CreateSessionRcOk> {
        return createRcLoginSession(recoveryCode)
            .flatMap { challenge ->
                houstonClient.loginWithRecoveryCode(signChallenge.sign(recoveryCode, challenge))
                    .flatMap { createSessionRcOk ->
                        decryptAndStoreKeySet(createSessionRcOk.keySet, recoveryCode)
                            .map { createSessionRcOk }
                    }
            }
    }

    /**
     * Creates a new session to log into Houston, associated with a given Recovery Code.
     */
    private fun createRcLoginSession(@NotNull recoveryCode: String) =
        getFcmToken.action()
            .flatMap { fcmToken ->
                houstonClient.createRcLoginSession(
                        Globals.INSTANCE.oldBuildType,
                        Globals.INSTANCE.versionCode,
                        fcmToken,
                        Libwallet.recoveryCodeToKey(recoveryCode, null).pubKeyHex(),
                        firebaseInstalationIdRepository.getBigQueryPseudoId()
                )
            }
    // TODO set RcChallengePublicKey in logging Context to help debug login issues

    private fun decryptAndStoreKeySet(maybeKeySet: Optional<KeySet>, rc: String): Observable<Void> =
        if (maybeKeySet.isPresent) {
            decryptAndStoreKeySet.action(maybeKeySet.get(), rc)
        } else {
            Observable.just(null)
        }
}

