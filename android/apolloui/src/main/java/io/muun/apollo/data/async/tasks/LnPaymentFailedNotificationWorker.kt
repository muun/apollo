package io.muun.apollo.data.async.tasks

import android.content.Context
import android.os.SystemClock
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.os.OS.supportsWorkManagerStopReason
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_WORKMANAGER_TASK_STARTED
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_WORKMANAGER_TASK_STOPPED
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_WORKMANAGER_TASK_SUCCESS
import io.muun.apollo.domain.model.LnUrlWithdraw

/**
 * Constructor. This is now called from background (WorkManager handles it) so dependency
 * injection is handled by a WorkFactory. See MuunWorkerFactory.
 */
class LnPaymentFailedNotificationWorker(
    val context: Context,
    val params: WorkerParameters,
    val notificationService: NotificationService,
    val analytics: Analytics,
) : Worker(context, params) {

    companion object {
        const val LNURL_WITHDRAW = "lnUrlWithdraw"
    }

    private var startMs: Long? = null

    override fun doWork(): Result {

        startMs = SystemClock.elapsedRealtime()

        analytics.report(E_WORKMANAGER_TASK_STARTED("LnPaymentFailedNotification", startMs!!))

        val inputData = params.inputData
        val lnUrlWithdraw = LnUrlWithdraw.deserialize(inputData.getString(LNURL_WITHDRAW)!!)

        notificationService.showLnPaymentExpiredNotification(lnUrlWithdraw)

        analytics.report(
            E_WORKMANAGER_TASK_SUCCESS(
                "LnPaymentFailedNotification",
                SystemClock.elapsedRealtime() - startMs!!
            )
        )

        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()

        var stopReason: Int? = null

        if (supportsWorkManagerStopReason()) {
            stopReason = getStopReason()
        }

        val durationInMillis = if (startMs != null) {
            SystemClock.elapsedRealtime() - startMs!!
        } else {
            null
        }

        analytics.report(
            E_WORKMANAGER_TASK_STOPPED(
                "LnPaymentFailedNotification",
                mapStopReason(stopReason),
                durationInMillis
            )
        )
    }
}