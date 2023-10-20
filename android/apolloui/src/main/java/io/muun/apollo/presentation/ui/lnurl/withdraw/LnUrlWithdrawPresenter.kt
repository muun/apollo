package io.muun.apollo.presentation.ui.lnurl.withdraw

import android.os.Bundle
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.domain.action.lnurl.LnUrlWithdrawAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.LnUrlWithdraw
import io.muun.apollo.domain.model.lnurl.LnUrlError
import io.muun.apollo.domain.model.lnurl.LnUrlState
import io.muun.apollo.domain.selector.WaitForIncomingLnPaymentSelector
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.bundler.LnUrlWithdrawBundler
import io.muun.apollo.presentation.ui.bundler.LnUrlWithdrawErrorBundler
import io.muun.apollo.presentation.ui.utils.UiNotificationPoller
import rx.Observable
import javax.inject.Inject

@PerActivity
class LnUrlWithdrawPresenter @Inject constructor(
    private val lnUrlWithdrawAction: LnUrlWithdrawAction,
    private val waitForIncomingLnPaymentSel: WaitForIncomingLnPaymentSelector,
    private val notificationService: NotificationService,
    private val notificationPoller: UiNotificationPoller,
) : BasePresenter<LnUrlWithdrawView>() {

    @State(LnUrlWithdrawErrorBundler::class)
    @JvmField
    var error: LnUrlError? = null

    @State(LnUrlWithdrawBundler::class)
    lateinit var lnUrlWithdraw: LnUrlWithdraw

    @State
    @JvmField
    var success: Boolean = false

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        // Pull notifications to speed up the LN payment reception
        notificationPoller.start()

        val observable: Observable<LnUrlState> = lnUrlWithdrawAction
            .state
            .compose(handleStates(null, this::handleError))
            .doOnNext { state ->
                handleWithdrawState(state)
            }

        subscribeTo(observable)

        // Special case: If we are entering from a notification
        arguments.getString(LnUrlWithdrawView.ARG_LNURL_WITHDRAW)?.let {
            lnUrlWithdraw = LnUrlWithdraw.deserialize(it)

            if (arguments.getBoolean(LnUrlWithdrawView.ARG_LN_PAYMENT_FAILED)) {

                // Special case for entering from ln payment failed notification
                val expiredInvoiceError = LnUrlError.ExpiredInvoice(
                    lnUrlWithdraw.service,
                    lnUrlWithdraw.invoice
                )
                handleWithdrawState(LnUrlState.Failed(expiredInvoiceError))
            }
        }

        // If activity is being re-created avoid re-firing another withdraw if:
        //
        // - we were "advanced" in the withdraw process ("advanced" defined by having already
        // generated an invoice and waiting for ln payment reception)
        // - we've already receive an error state
        if (!waitingForPayment() && error == null) {
            executeLnUrlWithdraw(arguments)

        } else if (error == null) {
            // If we are re-creating after being "advanced" in the withdraw process, let's jump to
            // Service Taking Too Long state (note: if we are here it means we were waiting for
            // ln payment to arrive).
            handleWithdrawState(LnUrlState.TakingTooLong(lnUrlWithdraw.service))

            // And let's start observing if that withdraw payment arrives!
            subscribeTo(waitForIncomingLnPaymentSel.watchInvoice(lnUrlWithdraw.invoice)) {
                view.finishActivity()
            }
        }
        // else we're re-creating after reaching an error state, we let the views (error fragment)
        // take care of their normal recreation
    }

    private fun handleWithdrawState(state: LnUrlState) {

        analytics.report(AnalyticsEvent.E_LNURL_WITHDRAW_STATE(eventType(state)))

        when (state) {
            is LnUrlState.Failed -> this.error = state.error

            is LnUrlState.Receiving -> {
                this.lnUrlWithdraw = LnUrlWithdraw(
                    getLnUrl(view.argumentsBundle),
                    state.domain,
                    state.invoice
                )
            }

            else -> {
                // ignore
            }
        }

        view.handleWithdrawState(state)
    }

    fun handleSuccess() {
        this.success = true
        goBackHome()
    }

    fun handleBack() {
        if (error != null) {
            goBackHome()
        }
        // else ignore, aka disable back button while withdraw in process
    }

    fun goBackHome() {
        navigator.navigateToHome(context)
        view.finishActivity()
    }

    fun handleRetry() {
        executeLnUrlWithdraw(view.argumentsBundle)
    }

    private fun executeLnUrlWithdraw(arguments: Bundle) {
        lnUrlWithdrawAction.reset()
        lnUrlWithdrawAction.run(getLnUrl(arguments))
    }

    fun handleSendReport() {
        sendErrorReport(error!!.toMuunError())
    }

    fun handleErrorDescriptionClicked() {

        // Assigning to a local variable makes error "inmutable" to kotlin and allows smart casts
        when (error) {
            is LnUrlError.ExpiredInvoice -> {
                clipboardManager.copy("LNURL Expired Invoice", lnUrlWithdraw.invoice)
                view.showTextToast(context.getString(R.string.operation_detail_invoice_copied))
            }
            else -> {
                // Do Nothing
            }
        }
    }

    override fun tearDown() {
        super.tearDown()

        notificationPoller.stop()

        if (!success && waitingForPayment() && error == null) {
            notificationService.showWaitingForLnPaymentNotification(lnUrlWithdraw)
            notificationService.scheduleLnPaymentExpirationNotification(lnUrlWithdraw)
        }
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_LNURL_WITHDRAW()
    }

    private fun eventType(state: LnUrlState): AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE =
        when (state) {
            is LnUrlState.Contacting -> AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE.CONTACTING
            is LnUrlState.InvoiceCreated -> AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE.INVOICE_CREATED
            is LnUrlState.Receiving -> AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE.RECEIVING
            is LnUrlState.TakingTooLong -> AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE.TAKING_TOO_LONG
            is LnUrlState.Failed -> AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE.FAILED
            is LnUrlState.Success -> AnalyticsEvent.LNURL_WITHDRAW_STATE_TYPE.SUCCESS
        }

    private fun waitingForPayment(): Boolean =
        ::lnUrlWithdraw.isInitialized

    private fun getLnUrl(arguments: Bundle): String =
        arguments.getString(LnUrlWithdrawView.ARG_LNURL)!!

}
