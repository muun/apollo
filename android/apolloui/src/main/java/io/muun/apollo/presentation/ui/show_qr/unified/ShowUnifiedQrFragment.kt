package io.muun.apollo.presentation.ui.show_qr.unified

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.core.widget.NestedScrollView
import butterknife.BindView
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.libwallet.DecodedBitcoinUri
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.MuunCountdownTimer
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.select_amount.SelectAmountActivity
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.utils.ReceiveLnInvoiceFormatter
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.openInBrowser
import io.muun.apollo.presentation.ui.view.AddressTypeItem
import io.muun.apollo.presentation.ui.view.ExpirationTimeItem
import io.muun.apollo.presentation.ui.view.HiddenSection
import io.muun.apollo.presentation.ui.view.LoadingView
import javax.money.MonetaryAmount

class ShowUnifiedQrFragment : QrFragment<ShowUnifiedQrPresenter>(), ShowUnifiedQrView,
    AddressTypeItem.AddressTypeChangedListener {

    companion object {
        private const val REQUEST_AMOUNT = 3
    }

    @BindView(R.id.unified_qr_scrollView)
    lateinit var scrollView: NestedScrollView

    @BindView(R.id.qr_overlay)
    lateinit var qrOverlay: View

    @BindView(R.id.unified_qr_settings)
    lateinit var hiddenSection: HiddenSection

    @BindView(R.id.unified_qr_settings_content)
    lateinit var uriSettingsContent: View

    @BindView(R.id.expiration_time_item)
    lateinit var expirationTimeItem: ExpirationTimeItem

    @BindView(R.id.unified_qr_loading)
    lateinit var loadingView: LoadingView

    @BindView(R.id.unified_qr_invoice_expired_overlay)
    lateinit var invoiceExpiredOverlay: View

    @BindView(R.id.create_other_invoice)
    lateinit var createOtherInvoice: View

    @BindView(R.id.address_type_item)
    lateinit var addressTypeItem: AddressTypeItem

    // State:

    // Part of our (ugly) hack to allow SATs as an input currency option
    @State
    @JvmField
    var satSelectedAsCurrency = false

    private var countdownTimer: MuunCountdownTimer? = null

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.fragment_show_qr_unified
    }

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        parentActivity.header.apply {
            visibility = View.VISIBLE
            showTitle(R.string.show_unified_qr_title)
        }

        addressTypeItem.setOnAddressTypeChangedListener(this)
        hiddenSection.setOnClickListener { presenter.toggleAdvancedSettings() }
        createOtherInvoice.setOnClickListener { onCreateInvoiceClick() }
        hiddenSection.setExpanded(false)
    }

    override fun setLoading(loading: Boolean) {
        loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        qrContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE // Keep view Bounds

        copyButton.isEnabled = !loading
        shareButton.isEnabled = !loading

        editAmountItem.setLoading(loading)
        expirationTimeItem.setLoading(loading)
        addressTypeItem.isClickable = !loading
    }

    override fun setBitcoinUri(
        bitcoinUri: DecodedBitcoinUri,
        addressType: AddressType,
        amount: MonetaryAmount?,
    ) {
        val uriString = bitcoinUri.getUriFor(addressType)

        // For this experimental feature we just display the on-chain address under the QR
        val address = bitcoinUri.addressGroup.getAddress(addressType)
        super.setQrContent("🧪 $address", uriString)

        addressTypeItem.show(addressType)

        stopTimer()
        countdownTimer = buildCountDownTimer(bitcoinUri.invoice.remainingMillis())
        countdownTimer!!.start()

        if (amount != null) {
            editAmountItem.setAmount(amount, getBitcoinUnit())

        } else {
            editAmountItem.resetAmount()
        }
    }

    override fun setTaprootState(status: UserActivatedFeatureStatus) {
        addressTypeItem.taprootStatus = status
    }

    override fun onStop() {
        super.onStop()
        stopTimer()
    }

    override fun resetAmount() {
        presenter.setAmount(null)
    }

    override fun showFullContent(bitcoinUri: String, address: String, invoice: String) {
        val dialog = TitleAndDescriptionDrawer()

        StyledStringRes(requireContext(), R.string.show_unified_qr_full_content, this::learnMore)
            .toCharSequence(address, invoice)
            .let(dialog::setDescription)

        showDrawerDialog(dialog)
    }

    private fun learnMore(link: String) {
        requireContext().openInBrowser(link)
    }

    override fun toggleAdvancedSettings() {
        hiddenSection.toggleSection()

        if (uriSettingsContent.visibility == View.VISIBLE) {
            uriSettingsContent.visibility = View.GONE

        } else {
            uriSettingsContent.visibility = View.VISIBLE

            scrollView.postDelayed({
                scrollView.fullScroll(View.FOCUS_DOWN)
            }, 100)
        }
    }

    override fun onAddressTypeChanged(newType: AddressType) {
        presenter.switchAddressType(newType)
    }

    override fun onEditAmount(amount: MonetaryAmount?) {
        requestDelegatedExternalResult(
            REQUEST_AMOUNT,
            SelectAmountActivity.getSelectInvoiceAmountIntent(
                requireContext(),
                amount,
                satSelectedAsCurrency
            )
        )
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onExternalResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_AMOUNT && resultCode == Activity.RESULT_OK) {
            this.satSelectedAsCurrency = SelectAmountActivity.getSatSelectedAsCurrencyResult(data!!)
            val result = SelectAmountActivity.getResult(data)

            if (result != null && !result.isZero) {
                presenter.setAmount(result)

            } else {
                resetAmount()
            }
        }
    }

    // ErrorCorrectionLevel.L makes the QR less dense. H only makes sense for legacy address where
    // the address itself doesn't have a checksum.
    override fun getErrorCorrection(): ErrorCorrectionLevel =
        ErrorCorrectionLevel.L

    private fun onCreateInvoiceClick() {
        presenter.generateNewEmptyUri()
        resetViewState()
    }

    private fun resetViewState() {
        invoiceExpiredOverlay.visibility = View.GONE
        qrOverlay.visibility = View.VISIBLE
        hiddenSection.visibility = View.VISIBLE
    }

    private fun stopTimer() {
        if (countdownTimer != null) {
            countdownTimer!!.cancel()
            countdownTimer = null
        }
    }

    private fun buildCountDownTimer(remainingMillis: Long): MuunCountdownTimer {

        return object : MuunCountdownTimer(remainingMillis) {

            override fun onTickSeconds(remainingSeconds: Long) {
                // Using minimalistic pattern/display/formatting to better fit in small screen space
                expirationTimeItem.setExpirationTime(
                    ReceiveLnInvoiceFormatter().formatSeconds(remainingSeconds)
                )
            }

            override fun onFinish() {
                invoiceExpiredOverlay.visibility = View.VISIBLE
                qrOverlay.visibility = View.GONE
                hiddenSection.visibility = View.GONE
                uriSettingsContent.visibility = View.GONE
            }
        }
    }

    // Part of our (ugly) hack to allow SATs as an input currency option
    private fun getBitcoinUnit() = if (satSelectedAsCurrency) {
        BitcoinUnit.SATS
    } else {
        BitcoinUnit.BTC
    }
}