package io.muun.apollo.presentation.ui.show_qr.ln

import android.os.Bundle
import icepick.State
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.domain.selector.LatestOperationSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.bundler.BitcoinAmountBundler
import io.muun.apollo.presentation.ui.show_qr.QrPresenter
import io.muun.common.utils.Encodings
import io.muun.common.utils.LnInvoice
import org.bitcoinj.core.NetworkParameters
import javax.inject.Inject

@PerFragment
class LnInvoiceQrPresenter @Inject constructor(
    private val generateInvoice: GenerateInvoiceAction,
    private val latestOperation: LatestOperationSelector,
    private val currencyDisplayModeSel: CurrencyDisplayModeSelector,
    private val networkParameters: NetworkParameters
) : QrPresenter<LnInvoiceView>() {

    @State
    lateinit var invoice: String

    @State(BitcoinAmountBundler::class)
    @JvmField
    var amount: BitcoinAmount? = null

    // We need to state-save in presenter 'cause apparently this fragment being inside ViewPager
    // messes up our state saving/restoring for our custom views :'(
    @State
    @JvmField
    var showingAdvancedSettings = false

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        view.setCurrencyDisplayMode(currencyDisplayModeSel.get())
        view.setShowingAdvancedSettings(showingAdvancedSettings)

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
                generateNewEmptyInvoice()
            }
        }
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

    override fun showFullContent() {
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

    fun toggleAdvancedSettings() {
        showingAdvancedSettings = !showingAdvancedSettings
    }

    private fun handleLoading(loading: Boolean) {
        view.setLoading(loading)
    }

    private fun onInvoiceReady(invoice: String) {
        this.invoice = invoice
        view.setInvoice(LnInvoice.decode(networkParameters, invoice), amount?.inInputCurrency)
    }
}
