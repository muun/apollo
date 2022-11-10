package io.muun.apollo.domain.action.fcm

import io.muun.apollo.data.async.gcm.FirebaseManager
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.errors.fcm.FcmTokenNotAvailableError
import io.muun.apollo.domain.errors.fcm.GooglePlayServicesNotAvailableError
import io.muun.apollo.domain.utils.replaceTypedError
import rx.Observable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFcmTokenAction @Inject constructor(
    private val firebaseInstallationIdRepository: FirebaseInstallationIdRepository,
    private val transformerFactory: ExecutionTransformerFactory,
    private val firebaseManager: FirebaseManager,
    private val googlePlayServicesHelper: GooglePlayServicesHelper,
) : BaseAsyncAction0<String>() {

    /**
     * Return the current FCM token, waiting for a few seconds if it's not immediately available.
     */
    override fun action(): Observable<String> =
        Observable.defer {
            firebaseInstallationIdRepository.watchFcmToken()
                .observeOn(transformerFactory.backgroundScheduler)
                .filter { token -> token != null }
                .map { token -> token!! }   // Just to appease Kotlin type inference
                .first()
                .timeout(25, TimeUnit.SECONDS)
                .replaceTypedError(TimeoutException::class.java) { getError() }
                .doOnError { Timber.e(it) } // force-log this UserFacingError
                .doOnError { firebaseManager.fetchFcmToken() }
                .retry(2)
        }

    private fun getError() =
        if (googlePlayServicesHelper.isAvailable != GooglePlayServicesHelper.AVAILABLE)
            GooglePlayServicesNotAvailableError()
        else
            FcmTokenNotAvailableError()
}
