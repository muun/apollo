package io.muun.apollo.presentation.ui.utils

import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.net.base.ServerFailureException
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.action.NotificationPoller
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByError
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByNetworkError
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByTimeoutError
import rx.Observable
import rx.Subscription
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// TODO open to make tests work with mockito. We should probably move to mockK
@OpenForTesting
open class UiNotificationPoller @Inject constructor(
    private val notificationPoller: NotificationPoller,
    private val transformerFactory: ExecutionTransformerFactory,
) {

    companion object {
        private val CI = Globals.INSTANCE.isCI
        private val POLL_INTERVAL_IN_SECS = if (CI) 5 else 2
    }

    private var subscription: Subscription? = null

    private var pollingIntervalInMillis = POLL_INTERVAL_IN_SECS * 1000L

    @VisibleForTesting
    fun setPollingIntervalInMillisForTesting(pollingIntervalInMillis: Int) {
        this.pollingIntervalInMillis = pollingIntervalInMillis.toLong()
    }

    /**
     * Start polling notifications every `POLL_INTERVAL_IN_SECS` seconds, in background. Note that
     * the first call will happen immediately, rather than wait for the first interval.
     */
    fun start() {
        if (subscription == null || subscription!!.isUnsubscribed) {
            subscription = subscribeToNotificationPolling()
        }
    }

    /**
     * Stop polling.
     */
    fun stop() {
        if (subscription != null && !subscription!!.isUnsubscribed) {
            subscription!!.unsubscribe()
            subscription = null
        }
    }

    private fun subscribeToNotificationPolling(): Subscription {
        Timber.d("subscribeToNotificationPolling")
        val backgroundScheduler = transformerFactory.backgroundScheduler
        return Observable
            .interval(pollingIntervalInMillis, TimeUnit.MILLISECONDS, backgroundScheduler)
            .startWith(0L)
            .onBackpressureLatest()
            .concatMap { notificationPoller.pullNotifications() }
            .compose(transformerFactory.getAsyncExecutor())
            .subscribe({}, { error -> handleError(error) })
    }

    private fun handleError(error: Throwable) {
        Timber.e(error)
        // We only re-start polling if it makes sense for the thrown error. E.g if
        // ExpiredSessionError we stop polling to avoid Houston hits that we know that will return
        // error (and probably fire monitoring alerts), otherwise we resubscribe to continue polling
        if (error.isInstanceOrIsCausedByNetworkError()
            || error.isInstanceOrIsCausedByTimeoutError()
            || error.isInstanceOrIsCausedByError<ServerFailureException>()
        ) {
            subscription = subscribeToNotificationPolling()
        }
    }
}