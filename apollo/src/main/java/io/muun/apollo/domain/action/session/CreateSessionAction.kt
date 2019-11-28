package io.muun.apollo.domain.action.session

import io.muun.apollo.data.async.gcm.FirebaseManager
import io.muun.apollo.data.logging.LoggingContext
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.errors.FcmTokenNotAvailableError
import io.muun.apollo.domain.utils.replaceTypedError
import io.muun.apollo.external.Globals
import io.muun.common.model.CreateSessionOk
import rx.Observable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import javax.validation.constraints.NotNull

@Singleton
class CreateSessionAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository,
    private val transformerFactory: ExecutionTransformerFactory,
    private val firebaseManager: FirebaseManager

): BaseAsyncAction1<String, CreateSessionOk>() {

    override fun action(email: String) =
        Observable.defer { createSession(email) }

    /**
     * Creates a new session in Houston, associated with a given email.
     */
    private fun createSession(@NotNull email: String) =
        waitForFcmToken()
            .flatMap<CreateSessionOk> { token ->
                houstonClient.createSession(
                    email,
                    Globals.INSTANCE.oldBuildType,
                    Globals.INSTANCE.versionCode,
                    token
                )
            }
            .doOnNext { LoggingContext.configure(email, "NotLoggedYet") }
            .doOnNext { userRepository.storeHasRecoveryCode(it.canUseRecoveryCode()) }

    /**
     * Return the current FCM token, waiting for a few seconds if it's not immediately available.
     */
    private fun waitForFcmToken(): Observable<String> {
        return userRepository.watchFcmToken()
            .observeOn(transformerFactory.backgroundScheduler)
            .filter { token -> token != null }
            .first()
            .timeout(15, TimeUnit.SECONDS)
            .replaceTypedError(TimeoutException::class.java) { FcmTokenNotAvailableError() }
            .doOnError { Timber.e(it) } // force-log this UserFacingError
            .doOnError { firebaseManager.fetchFcmToken() }
            .retry(1)
    }
}
