package io.muun.apollo.domain.action.debug

import com.crashlytics.android.Crashlytics
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.external.Globals
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForceCrashReportAction @Inject constructor(): BaseAsyncAction1<String, Void>() {

    class ForcedCrashlyticsCall(origin: String):
        RuntimeException("Forced logException $origin v${Globals.INSTANCE.versionCode}")

    class ForcedTimberErrorCall(origin: String):
        RuntimeException("Forced Timber.e $origin v${Globals.INSTANCE.versionCode}")

    class ForcedBackgroundException(origin: String):
        RuntimeException("Forced throw $origin v${Globals.INSTANCE.versionCode}")

    /**
     * Forcibly send a crash report to Crashlytics, for testing purposes.
     */
    override fun action(origin: String) =
        Observable.defer {
            Timber.e(ForcedTimberErrorCall(origin))
            Crashlytics.logException(ForcedCrashlyticsCall(origin))
            throw ForcedBackgroundException(origin)

            Observable.just<Void>(null)
        }

}
