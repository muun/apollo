package io.muun.apollo.presentation.ui.show_qr.ln

import android.os.Bundle
import icepick.State
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.selector.LatestOperationSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.show_qr.QrPresenter
import io.muun.common.utils.Encodings
import io.muun.common.utils.LnInvoice
import org.bitcoinj.core.NetworkParameters
import javax.inject.Inject

@PerFragment
class LnInvoiceQrPresenter @Inject constructor(
    private val generateInvoice: GenerateInvoiceAction,
    private val latestOperation: LatestOperationSelector,
    private val networkParameters: NetworkParameters
) : QrPresenter<LnInvoiceView>() {

    @State
    lateinit var invoice: String

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        generateInvoice
            .state
            .compose(handleStates({ loading -> handleLoading(loading) }, { e -> handleError(e) }))
            .doOnNext { invoice -> onInvoiceReady(invoice) }
            .let(this::subscribeTo)

        // We want to re-generate the invoice each time we come back to this fragment.
        generateNewInvoice()

        subscribeTo(latestOperation.watch()) { maybeOp ->
            maybeOp.ifPresent(this::onNewOperation)
        }
    }

    private fun onNewOperation(op: Operation) {
        if (::invoice.isInitialized && op.incomingSwap != null) {
            val receivedPaymentHash = Encodings.bytesToHex(op.incomingSwap!!.paymentHash)
            val paymentHashInDisplay = LnInvoice.decode(networkParameters, invoice).id
            if (receivedPaymentHash == paymentHashInDisplay) {
                generateNewInvoice()
            }
        }
    }

    fun generateNewInvoice() {
        generateInvoice.reset()
        generateInvoice.run()
    }

    override fun hasLoadedCorrectly(): Boolean =
        ::invoice.isInitialized

    override fun getQrContent(): String {
        return invoice
    }

    override fun showFullContent() {
        view.showFullContent(invoice)
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_RECEIVE(
            AnalyticsEvent.S_RECEIVE_TYPE.LN_INVOICE,
            parentPresenter.getOrigin()
        )
    }

    private fun handleLoading(loading: Boolean) {
        view.setLoading(loading)
    }

    private fun onInvoiceReady(invoice: String) {
        this.invoice = invoice
        view.setInvoice(LnInvoice.decode(networkParameters, invoice))
    }
}
