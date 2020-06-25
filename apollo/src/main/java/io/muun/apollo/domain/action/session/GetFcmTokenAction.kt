package io.muun.apollo.domain.action.session

import io.muun.apollo.data.async.gcm.FirebaseManager
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.errors.FcmTokenNotAvailableError
import io.muun.apollo.domain.utils.replaceTypedError
import rx.Observable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFcmTokenAction @Inject constructor(
    private val userRepository: UserRepository,
    private val transformerFactory: ExecutionTransformerFactory,
    private val firebaseManager: FirebaseManager

): BaseAsyncAction0<String>() {

    /**
     * Return the current FCM token, waiting for a few seconds if it's not immediately available.
     */
    override fun action() =
        Observable.defer {
            userRepository.watchFcmToken()
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
