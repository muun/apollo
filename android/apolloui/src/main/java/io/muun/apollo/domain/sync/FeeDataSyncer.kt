package io.muun.apollo.domain.sync

import android.os.Handler
import android.os.Looper
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.action.NotificationActions
import io.muun.apollo.domain.action.NotificationProcessingState
import io.muun.apollo.domain.action.realtime.PreloadFeeDataAction
import io.muun.apollo.domain.libwallet.FeeBumpRefreshPolicy
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.apollo.domain.selector.FeatureSelector
import rx.subscriptions.CompositeSubscription
import timber.log.Timber
import javax.inject.Inject

/**
 * FeeDataSyncer handles NTS changes, observing NotificationActions
 * and deciding if it is necessary to refresh fee bump functions.
 * It is designed to perform only one refresh per batch of notifications
 * processed by NotificationProcessor. Please note that this should be called
 * in a serialized manner, meaning that the entire flow of 1 STARTED event
 * followed by 1 COMPLETED event must occur sequentially.
 * Besides, FeeDataSyncer starts a periodic task that runs every
 * `intervalInMilliseconds` milliseconds to update fee bump functions
 * in the background.
 */
class FeeDataSyncer @Inject constructor(
    private val preloadFeeData: PreloadFeeDataAction,
    private val nextTransactionSizeRepository: TransactionSizeRepository,
    private val notificationActions: NotificationActions,
    private val featureSelector: FeatureSelector,
) {

    private var initialNts: NextTransactionSize? = null

    private val intervalInMilliseconds: Long = 60000 // 60 seconds
    private val compositeSubscription = CompositeSubscription()

    private val handler = Handler(Looper.getMainLooper())
    private val periodicTask = object : Runnable {
        override fun run() {
            preloadFeeData.run(FeeBumpRefreshPolicy.PERIODIC)
            try {
                handler.postDelayed(this, intervalInMilliseconds)
                Timber.d("[Sync] Scheduling FeeDataSync periodic task")
            } catch (e: Exception) {
                Timber.e(e, "Fee data periodic task failed.")
            }
        }
    }

    fun enterForeground() {
        if (!featureSelector.get(MuunFeature.EFFECTIVE_FEES_CALCULATION)) {
            return
        }

        startPeriodicTask()
        beginObservingNtsChanges()
    }

    fun enterBackground() {
        stopPeriodicTask()
        stopObservingNtsChanges()
    }

    /**
     * Starts a task that runs every `intervalInMilliseconds` milliseconds while
     * the app is in foreground to keep the fee data up-to-date.
     */
    private fun startPeriodicTask() {
        handler.removeCallbacksAndMessages(periodicTask) // to avoid duplicated tasks
        handler.postDelayed(periodicTask, intervalInMilliseconds)
    }

    private fun stopPeriodicTask() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun beginObservingNtsChanges() {
        notificationActions.getNotificationProcessingState()
            .doOnNext { state ->
                when (state) {
                    NotificationProcessingState.STARTED -> {
                        initialNts = nextTransactionSizeRepository.nextTransactionSize
                    }

                    NotificationProcessingState.COMPLETED -> {
                        if (shouldUpdateFeeBumpFunctions()) {
                            preloadFeeData.runForced(FeeBumpRefreshPolicy.NTS_CHANGED)
                        }
                    }

                    else -> {
                        // ignore
                    }
                }
            }.subscribe()
            .also { subscription -> compositeSubscription.add(subscription) }
    }

    private fun stopObservingNtsChanges() {
        compositeSubscription.clear()
    }

    private fun shouldUpdateFeeBumpFunctions(): Boolean {
        nextTransactionSizeRepository.nextTransactionSize?.let { currentNts ->
            val newUnconfirmedUtxos = currentNts.unconfirmedUtxos
            if (newUnconfirmedUtxos.isNotEmpty()) {
                val previousUnconfirmedUtxos = initialNts?.unconfirmedUtxos
                return newUnconfirmedUtxos != previousUnconfirmedUtxos
            }
            return false
        }
        return false
    }
}