package io.muun.apollo.domain.action.session

import io.muun.apollo.data.logging.LoggingContext
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.CurrencyActions
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.action.keys.CreateChallengeSetupAction
import io.muun.apollo.domain.action.keys.CreateRootPrivateKeyAction
import io.muun.apollo.domain.action.keys.StoreChallengeKeyAction
import io.muun.apollo.domain.model.CreateFirstSessionOk
import io.muun.apollo.domain.utils.flatDoOnNext
import io.muun.apollo.external.Globals
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.utils.Encodings
import io.muun.common.utils.RandomGenerator
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateFirstSessionAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository,
    private val keysRepository: KeysRepository,
    private val currencyActions: CurrencyActions,
    private val getGetFcmToken: GetFcmTokenAction,
    private val createRootPrivateKey: CreateRootPrivateKeyAction,
    private val createChallengeSetup: CreateChallengeSetupAction,
    private val storeChallengeKey: StoreChallengeKeyAction

): BaseAsyncAction0<CreateFirstSessionOk>() {

    /**
     * Creates a new user with their first session in Houston.
     */
    override fun action() =
        Observable.defer {
            getGetFcmToken.action().flatMap { setUpUser(it) }
        }

    /**
     * Signs up a user.
     */
    private fun setUpUser(gcmToken: String): Observable<CreateFirstSessionOk> {
        val salt = RandomGenerator.getBytes(8)
        val anonSecret = Encodings.bytesToHex(RandomGenerator.getBytes(32))

        val anonPublicKey = ChallengePrivateKey
            .fromUserInput(anonSecret, salt)
            .challengePublicKey

        return keysRepository.storeAnonSecret(anonSecret)
            .flatMap {
                createRootPrivateKey.action(anonSecret)
            }
            .flatMap {
                createChallengeSetup.action(ChallengeType.ANON, anonSecret)
            }
            .flatMap { chSetup ->
                houstonClient
                    .createFirstSession(
                        Globals.INSTANCE.oldBuildType,
                        Globals.INSTANCE.versionCode,
                        gcmToken,
                        keysRepository.basePublicKey,
                        chSetup,
                        currencyActions.localCurrencies.iterator().next()
                    )
                    .flatDoOnNext {
                        storeChallengeKey.action(chSetup.type, chSetup.publicKey)
                    }
            }
            .doOnNext {
                userRepository.store(it.user)
                keysRepository.storeBaseMuunPublicKey(it.cosigningPublicKey)
                keysRepository.storePublicChallengeKey(anonPublicKey, ChallengeType.ANON)

                LoggingContext.configure("unknown", it.user.hid.toString())
            }
    }
}
