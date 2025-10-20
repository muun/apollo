package io.muun.apollo.presentation.ui.show_qr

import android.os.Build
import androidx.annotation.RequiresApi
import io.muun.apollo.R
import io.muun.apollo.domain.action.notification.SetNotificationPermissionDeniedAction
import io.muun.apollo.domain.action.notification.SetNotificationPermissionSkippedAction
import io.muun.apollo.domain.action.permission.SetNotificationPermissionNeverAskAgainAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.PermissionState
import io.muun.apollo.domain.selector.NotificationPermissionPreviouslyDeniedSelector
import io.muun.apollo.domain.selector.NotificationPermissionSkippedSelector
import io.muun.apollo.domain.selector.NotificationsPermissionStateSelector
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.scan_qr.LnUrlFlow
import io.muun.apollo.presentation.ui.show_qr.ShowQrActivity.ORIGIN
import io.muun.common.model.ReceiveFormatPreference
import javax.inject.Inject

@PerActivity
class ShowQrPresenter @Inject constructor(
    private val userPreferencesSel: UserPreferencesSelector,
    private val notificationPermissionSkippedSel: NotificationPermissionSkippedSelector,
    private val notificationPermPreviouslyDenied: NotificationPermissionPreviouslyDeniedSelector,
    private val notificationsPermissionStateSel: NotificationsPermissionStateSelector,
    private val setNotificationPermissionSkipped: SetNotificationPermissionSkippedAction,
    private val setNotificationPermissionNeverAskAgain: SetNotificationPermissionNeverAskAgainAction,
    private val setNotificationPermissionDenied: SetNotificationPermissionDeniedAction,
) : BasePresenter<ShowQrView>(), QrParentPresenter {

    override fun shareQrContent(content: String) {
        navigator.shareText(context, content, context.getString(R.string.address_share_title))
        analytics.report(AnalyticsEvent.E_ADDRESS_SHARE_TOUCHED())
    }

    override fun copyQrContent(content: String, origin: AnalyticsEvent.ADDRESS_ORIGIN) {
        clipboardManager.copyQrContent(content)
        view.showTextToast(context.getString(R.string.show_qr_copied))
        analytics.report(AnalyticsEvent.E_ADDRESS_COPIED(origin))
    }

    override fun getOrigin(): AnalyticsEvent.RECEIVE_ORIGIN {
        return view.argumentsBundle.getSerializable(ORIGIN) as AnalyticsEvent.RECEIVE_ORIGIN
    }

    fun startScanLnUrlFlow() {

        if (userPreferencesSel.get().seenLnurlFirstTime) {
            navigator.navigateToLnUrlWithdrawScanQr(context, LnUrlFlow.STARTED_FROM_RECEIVE)

        } else {
            navigator.navigateToLnUrlIntro(context)
        }
    }

    // TODO fragment should NOT "query" presenter. Presenter should convey state to fragment/view
    fun showUnifiedQr(): Boolean =
        receiveFormatPreference() == ReceiveFormatPreference.UNIFIED

    // TODO fragment should NOT "query" presenter. Presenter should convey state to fragment/view
    fun showFirstTimeNotificationPriming(): Boolean {
        val isBitcoinFirst = receiveFormatPreference() == ReceiveFormatPreference.ONCHAIN
        val permissionStateNotDetermined = notiPermissionState() == PermissionState.NOT_DETERMINED
        val hasSkippedFirstTimeNotiPriming = notificationPermissionSkippedSel.get()
        return isBitcoinFirst && permissionStateNotDetermined && !hasSkippedFirstTimeNotiPriming
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun handleNotificationPermissionPrompt() {
        if (notiPermissionState() != PermissionState.PERMANENTLY_DENIED) {
            view.requestNotificationPermission()
            reportNotificationPermissionAsked()

        } else {
            view.handleNotificationPermissionPromptWhenPermanentlyDenied()
        }
    }

    /**
     * Navigate to this app's System Settings, in order to manually change the push notification
     * permission.
     */
    fun navigateToSystemSettings() {
        navigator.navigateToSystemSettings(context)
    }

    fun reportNotificationPermissionSkipped() {
        analytics.report(AnalyticsEvent.E_PUSH_NOTI_PERMISSION_SKIPPED())
        setNotificationPermissionSkipped.run()
    }

    override fun reportNotificationPermissionAsked() {
        analytics.report(AnalyticsEvent.E_PUSH_NOTI_PERMISSION_ASKED())
    }

    private fun reportNotificationPermissionNeverAskAgain() {
        analytics.report(AnalyticsEvent.E_PUSH_NOTI_PERMISSION_PERMA_DECLINED())
        setNotificationPermissionNeverAskAgain.run()
    }

    fun reportNotificationPermissionDenied(shouldShowRequestPermissionRationale: Boolean) {
        analytics.report(AnalyticsEvent.E_PUSH_NOTI_PERMISSION_DECLINED())

        // Here we're abusing the fact that shouldShowRequestPermissionRationale starts in false
        // and turns to true the first time the user taps on "Don't allow" (e.g it remains false if
        // user dismissed system permission prompt by tapping outside the dialog). Once users taps
        // "Don't allow" a 2nd time, it turns false again.
        if (shouldShowRequestPermissionRationale) {
            // When turned true for the first time, means user explicitly tap in "Don't allow"
            setNotificationPermissionDenied.run()

        } else if (notificationPermPreviouslyDenied.get()) {
            // IF shouldShowLocationPermissionRationale() is false
            // && userHasPreviouslyDeniedNotificationPermission is true
            // THEN Permission has been explicitly denied twice
            // THEN System won't prompt again for Notification permission
            // THEN Permission is PermanentlyDenied
            reportNotificationPermissionNeverAskAgain()
        }
    }

    fun reportNotificationPermissionGranted() {
        analytics.report(AnalyticsEvent.E_PUSH_NOTI_PERMISSION_GRANTED())
        view.handleNotificationPermissionGranted()
    }

    private fun receiveFormatPreference() = userPreferencesSel.get().receivePreference

    private fun notiPermissionState() = notificationsPermissionStateSel.get()

}
