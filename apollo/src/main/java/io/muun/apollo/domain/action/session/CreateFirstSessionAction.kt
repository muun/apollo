package io.muun.apollo.domain.action.session

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.logging.LoggingContext
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.CurrencyActions
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
    private val getFcmToken: GetFcmTokenAction,
    private val createBasePrivateKey: CreateBasePrivateKeyAction

) : BaseAsyncAction0<CreateFirstSessionOk>() {

    /**
     * Creates a new user with their first session in Houston.
     */
    override fun action(): Observable<CreateFirstSessionOk> =
        Observable.defer {
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
                        Globals.INSTANCE.oldBuildType,
                        Globals.INSTANCE.versionCode,
                        gcmToken,
                        basePrivateKey.publicKey,
                        currencyActions.localCurrencies.iterator().next()
                    )
            }
            .doOnNext {
                userRepository.store(it.user)
                keysRepository.storeBaseMuunPublicKey(it.cosigningPublicKey)

                LoggingContext.configure("unknown", it.user.hid.toString())
            }
    }
}
