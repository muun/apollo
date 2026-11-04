package io.muun.apollo.osmodules.background_actions

import io.muun.apollo.data.net.NetworkStatusProvider
import io.muun.apollo.data.os.AppStateProvider
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.os.watchForeground
import io.muun.apollo.data.preferences.UserRepository
import org.threeten.bp.Duration
import rx.Observable
import rx.Subscription
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Absorbs network flaps and quick app switches, and stays well below the shortest background action
// period.
internal val FOREGROUND_RUN_COOLDOWN = Duration.ofMinutes(5)

@Singleton
class BackgroundActionsForegroundRunner @Inject constructor(
    private val scheduler: BackgroundActionsScheduler,
    private val userRepository: UserRepository,
    private val appStateProvider: AppStateProvider,
    private val networkStatusProvider: NetworkStatusProvider,
    private val transformerFactory: ExecutionTransformerFactory,
) {

    /**
     * Run the due background actions whenever the app becomes visible and online after each
     * initial sync, at most once per cooldown window. Nothing upstream can act on errors, so they
     * are logged.
     */
    fun start(): Subscription =
        userRepository.watchInitialSyncCompleted()
            // Each login starts a new session, with its own edges and cooldown.
            .switchOrEmpty {
                appStateProvider.watchForeground()
                    // Watch the network only while visible.
                    // This way we avoid unnecessary registrations, and acting on stale values.
                    .switchOrElse(false) {
                        networkStatusProvider.watchConnected()
                            .subscribeOn(transformerFactory.backgroundScheduler)
                            // A failed watch only drops this visit's run.
                            .doOnError { Timber.e(it) }
                            .onErrorReturn { false }
                    }
                    .distinctUntilChanged()
                    .filter { it }
                    // Logout clears the preference without emitting, so we re-check.
                    .takeWhile { userRepository.isInitialSyncCompleted }
                    .throttleFirst(
                        FOREGROUND_RUN_COOLDOWN.toMillis(),
                        TimeUnit.MILLISECONDS,
                        transformerFactory.backgroundScheduler
                    )
            }
            .subscribeOn(transformerFactory.backgroundScheduler)
            .observeOn(transformerFactory.backgroundScheduler)
            .subscribe({ runNow() }, { Timber.e(it) })

    private fun runNow() {
        try {
            scheduler.runNow()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun <T> Observable<Boolean>.switchOrElse(
        default: T,
        func: () -> Observable<T>,
    ): Observable<T> {
        return switchMap { bool ->
            if (bool) {
                func()
            } else {
                Observable.just(default)
            }
        }
    }

    private fun <T> Observable<Boolean>.switchOrEmpty(func: () -> Observable<T>): Observable<T> {
        return switchMap { bool ->
            if (bool) {
                func()
            } else {
                Observable.empty()
            }
        }
    }
}
