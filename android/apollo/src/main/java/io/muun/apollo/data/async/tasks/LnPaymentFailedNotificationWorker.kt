package io.muun.apollo.data.async.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.domain.model.LnUrlWithdraw

/**
 * Constructor. This is now called from background (WorkManager handles it) so dependency
 * injection is handled by a WorkFactory. See MuunWorkerFactory.
 */
class LnPaymentFailedNotificationWorker(
    val context: Context,
    val params: WorkerParameters,
    val notificationService: NotificationService
): Worker(context, params) {

    companion object {
        const val LNURL_WITHDRAW = "lnUrlWithdraw"
    }

    override fun doWork(): Result {

        val inputData = params.inputData
        val lnUrlWithdraw = LnUrlWithdraw.deserialize(inputData.getString(LNURL_WITHDRAW)!!)

        notificationService.showLnPaymentExpiredNotification(lnUrlWithdraw)

        return Result.success()
    }
}