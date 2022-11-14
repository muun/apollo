package io.muun.apollo.domain.action.session.rc_only

import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.challenge_keys.SignChallengeAction
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.action.keys.DecryptAndStoreKeySetAction
import io.muun.apollo.domain.action.session.IsRootedDeviceAction
import io.muun.common.Optional
import io.muun.common.api.KeySet
import io.muun.common.model.CreateSessionRcOk
import io.muun.common.model.challenge.Challenge
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
    private val isRootedDeviceAction: IsRootedDeviceAction,
    private val firebaseInstallationIdRepository: FirebaseInstallationIdRepository
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
    private fun createRcLoginSession(@NotNull recoveryCode: String): Observable<Challenge> {
        val pubKeyHex = Libwallet.recoveryCodeToKey(recoveryCode, null).pubKeyHex()
        val bigQueryPseudoId = firebaseInstallationIdRepository.getBigQueryPseudoId()
        Crashlytics.logBreadcrumb("Rc Login: $pubKeyHex")
        Crashlytics.logBreadcrumb("Rc Login: $bigQueryPseudoId")
        return getFcmToken.action()
            .flatMap { fcmToken ->
                houstonClient.createRcLoginSession(
                    fcmToken,
                    pubKeyHex,
                    bigQueryPseudoId,
                    isRootedDeviceAction.actionNow()
                )
            }
    }

    private fun decryptAndStoreKeySet(maybeKeySet: Optional<KeySet>, rc: String): Observable<Void> =
        if (maybeKeySet.isPresent) {
            decryptAndStoreKeySet.action(maybeKeySet.get(), rc)
        } else {
            Observable.just(null)
        }
}

