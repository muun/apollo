package io.muun.apollo.presentation.ui.utils

import androidx.annotation.OpenForTesting
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.action.NotificationActions
import io.muun.apollo.domain.errors.ExpiredSessionError
import rx.Observable
import rx.Subscription
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// TODO open to make tests work with mockito. We should probably move to mockK
@OpenForTesting
open class UiNotificationPoller @Inject constructor(
    private val notificationActions: NotificationActions,
    private val transformerFactory: ExecutionTransformerFactory,
) {

    companion object {
        private val CI = Globals.INSTANCE.oldBuildType == "regtestDebug"
        private val POLL_INTERVAL_IN_SECS = if (CI) 5 else 2
    }

    private var subscription: Subscription? = null

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
        return Observable
            .interval(POLL_INTERVAL_IN_SECS.toLong(), TimeUnit.SECONDS)
            .startWith(0L)
            .onBackpressureLatest()
            .concatMap { notificationActions.pullNotifications() }
            .compose(transformerFactory.getAsyncExecutor())
            .subscribe({}, { error -> handleError(error) })
    }

    private fun handleError(error: Throwable) {
        Timber.e(error)
        // If ExpiredSessionError we stop polling to avoid Houston hits that we know that will return
        // error (and probably fire monitoring alerts), otherwise we resubscribe to continue polling
        if (error !is ExpiredSessionError) {
            subscription = subscribeToNotificationPolling()
        }
    }
}