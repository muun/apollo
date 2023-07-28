package io.muun.apollo.presentation.ui.show_qr.ln

import android.os.Bundle
import icepick.State
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.libwallet.Invoice
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.domain.selector.WaitForIncomingLnPaymentSelector
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.bundler.BitcoinAmountBundler
import io.muun.apollo.presentation.ui.show_qr.QrPresenter
import javax.inject.Inject

@PerFragment
class LnInvoiceQrPresenter @Inject constructor(
    private val generateInvoice: GenerateInvoiceAction,
    private val waitForIncomingLnPaymentSel: WaitForIncomingLnPaymentSelector,
    private val featureSelector: FeatureSelector,
) : QrPresenter<LnInvoiceView>() {

    @State
    lateinit var invoice: String

    @State(BitcoinAmountBundler::class)
    @JvmField
    var amount: BitcoinAmount? = null

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        if (featureSelector.get(MuunFeature.HIGH_FEES_RECEIVE_FLOW)) {
            view.setShowHighFeesWarning()
        }
        view.setShowingAdvancedSettings(showingAdvancedSettings)

        generateInvoice
            .state
            .compose(handleStates(this::handleLoading, this::handleError))
            .doOnNext { invoice -> onInvoiceReady(invoice) }
            .let(this::subscribeTo)

        // We want to re-generate the invoice each time we come back to this fragment.
        view.refresh()
    }

    internal fun generateNewEmptyInvoice() {
        view.resetAmount()
    }

    fun generateNewInvoice() {
        generateInvoice.reset()
        generateInvoice.run(amount?.inSatoshis)
    }

    override fun hasLoadedCorrectly(): Boolean =
        ::invoice.isInitialized

    override fun getQrContent(): String {
        return invoice
    }

    override fun showFullContentInternal() {
        view.showFullContent(invoice)
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_RECEIVE(
            AnalyticsEvent.S_RECEIVE_TYPE.LN_INVOICE,
            parentPresenter.getOrigin()
        )
    }

    fun setAmount(bitcoinAmount: BitcoinAmount?) {
        this.amount = bitcoinAmount
        generateNewInvoice()
    }

    private fun handleLoading(loading: Boolean) {
        view.setLoading(loading)
    }

    private fun onInvoiceReady(invoice: String) {
        this.invoice = invoice
        view.setInvoice(
            Invoice.decodeInvoice(Globals.INSTANCE.network, invoice), amount?.inInputCurrency
        )

        subscribeTo(waitForIncomingLnPaymentSel.watchInvoice(invoice)) {
            generateNewEmptyInvoice()
        }
    }
}
