package io.muun.apollo.domain.action.session

import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.CurrencyActions
import io.muun.apollo.domain.action.LogoutActions
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.action.keys.CreateBasePrivateKeyAction
import io.muun.apollo.domain.model.CreateFirstSessionOk
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateFirstSessionAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository,
    private val keysRepository: KeysRepository,
    private val currencyActions: CurrencyActions,
    private val logoutActions: LogoutActions,
    private val getFcmToken: GetFcmTokenAction,
    private val createBasePrivateKey: CreateBasePrivateKeyAction,
    private val firebaseInstallationIdRepository: FirebaseInstallationIdRepository,
    private val isRootedDeviceAction: IsRootedDeviceAction,
) : BaseAsyncAction0<CreateFirstSessionOk>() {

    /**
     * Creates a new user with their first session in Houston.
     */
    override fun action(): Observable<CreateFirstSessionOk> =
        Observable.defer {
            logoutActions.destroyWalletToStartClean()
            getFcmToken.action().flatMap { setUpUser(it) }
        }

    /**
     * Signs up a user.
     */
    private fun setUpUser(gcmToken: String): Observable<CreateFirstSessionOk> {

        return createBasePrivateKey.action()
            .flatMap { basePrivateKey ->
                houstonClient
                    .createFirstSession(
                        gcmToken,
                        basePrivateKey.publicKey,
                        currencyActions.localCurrencies.iterator().next(),
                        firebaseInstallationIdRepository.getBigQueryPseudoId(),
                        isRootedDeviceAction.actionNow()
                    )
            }
            .doOnNext {
                userRepository.store(it.user)
                keysRepository.storeBaseMuunPublicKey(it.cosigningPublicKey)
                keysRepository.storeSwapServerPublicKey(it.swapServerPublicKey)

                Crashlytics.configure(null, it.user.hid.toString())
            }
    }
}
