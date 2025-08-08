package io.muun.apollo.domain.action.debug

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForceErrorReportAction @Inject constructor() : BaseAsyncAction1<String, Void>() {

    class ForcedCrashlyticsCall(origin: String) :
        RuntimeException("Forced logException $origin v${Globals.INSTANCE.versionCode}")

    class ForcedTimberErrorCall(origin: String) :
        RuntimeException("Forced Timber.e $origin v${Globals.INSTANCE.versionCode}")

    class ForcedBackgroundException(origin: String) :
        RuntimeException("Forced throw $origin v${Globals.INSTANCE.versionCode}")

    /**
     * Forcibly send a crash report to Crashlytics, for testing purposes.
     */
    override fun action(origin: String): Observable<Void> =
        Observable.defer {
            Timber.e(ForcedTimberErrorCall(origin))
            Crashlytics.forceReport(ForcedCrashlyticsCall(origin))
            throw ForcedBackgroundException(origin)
        }

}
