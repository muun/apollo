package io.muun.apollo.domain.action.session

import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.data.preferences.PlayIntegrityNonceRepository
import io.muun.apollo.domain.action.LogoutActions
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.model.CreateSessionOk
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateLoginSessionAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val getFcmToken: GetFcmTokenAction,
    private val logoutActions: LogoutActions,
    private val isRootedDeviceAction: IsRootedDeviceAction,
    private val firebaseInstallationIdRepo: FirebaseInstallationIdRepository,
    private val playIntegrityNonceRepo: PlayIntegrityNonceRepository,
) : BaseAsyncAction1<String, CreateSessionOk>() {

    override fun action(email: String): Observable<CreateSessionOk> =
        Observable.defer { createSession(email) }

    /**
     * Creates a new session to log into Houston, associated with a given email.
     */
    private fun createSession(email: String): Observable<CreateSessionOk> {

        logoutActions.destroyWalletToStartClean()

        return getFcmToken.action()
            .flatMap { fcmToken ->
                houstonClient.createLoginSession(
                    fcmToken,
                    email,
                    firebaseInstallationIdRepo.getBigQueryPseudoId(),
                    isRootedDeviceAction.actionNow()
                )
            }
            .doOnNext {
                Crashlytics.configure("NotLoggedYet")
                playIntegrityNonceRepo.store(it.playIntegrityNonce)
            }
    }
}
