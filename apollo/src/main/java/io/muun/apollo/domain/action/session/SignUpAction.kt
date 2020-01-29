package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.CurrencyActions
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.keys.CreateRootPrivateKeyAction
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SignUpAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userActions: UserActions,
    private val currencyActions: CurrencyActions,
    private val keysRepository: KeysRepository,
    private val createRootPrivateKey: CreateRootPrivateKeyAction

): BaseAsyncAction1<String, Void>() {

    override fun action(password: String) =
        Observable.defer { signUp(password) }

    /**
     * Signs up a user.
     */
    fun signUp(password: String): Observable<Void> {
        val salt = userActions.generateSaltForChallengeKey()

        val passwordPublicKey = ChallengePrivateKey
            .fromUserInput(password, salt)
            .challengePublicKey

        return Observable.defer {
            createRootPrivateKey.action(password)
                .flatMap { encryptedPrivateKey ->
                    val primaryCurrency = getDefaultPrimaryCurrency()

                    houstonClient.signup(
                        encryptedPrivateKey,
                        primaryCurrency,
                        keysRepository.basePublicKey,
                        passwordPublicKey,
                        salt
                    )
                }
            }
            .doOnNext { keysRepository.storeBaseMuunPublicKey(it) }
            .doOnNext { userActions.storeChallengeKey(ChallengeType.PASSWORD, passwordPublicKey) }
            .toVoid()
    }

    private fun getDefaultPrimaryCurrency() =
        currencyActions.localCurrencies.iterator().next()
}
