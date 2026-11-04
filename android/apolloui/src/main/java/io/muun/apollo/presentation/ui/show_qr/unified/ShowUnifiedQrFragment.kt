package io.muun.apollo.presentation.ui.show_qr.unified

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.viewbinding.ViewBinding
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentShowQrUnifiedBinding
import io.muun.apollo.domain.libwallet.DecodedBitcoinUri
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.MuunCountdownTimer
import io.muun.apollo.presentation.ui.select_amount.SelectAmountActivity
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.show_qr.UnifiedQrFullContentDialogFragment
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.utils.ReceiveLnInvoiceFormatter
import io.muun.apollo.presentation.ui.view.AddressTypeItem
import io.muun.apollo.presentation.ui.view.EditAmountItem
import io.muun.apollo.presentation.ui.view.MuunButton
import javax.money.MonetaryAmount

class ShowUnifiedQrFragment : QrFragment<ShowUnifiedQrPresenter>(),
    ShowUnifiedQrView,
    AddressTypeItem.AddressTypeChangedListener,
    MuunCountdownTimer.CountDownTimerListener {

    companion object {
        private const val REQUEST_AMOUNT = 3
    }

    private val binding: FragmentShowQrUnifiedBinding
        get() = getBinding() as FragmentShowQrUnifiedBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        FragmentShowQrUnifiedBinding::inflate

    override val qrImage: ImageView
        get() = binding.showQrImageQr
    override val qrContent: TextView
        get() = binding.showQrContent
    override val copyButton: MuunButton
        get() = binding.showQrCopy
    override val shareButton: MuunButton
        get() = binding.showQrShare
    override val editAmountItem: EditAmountItem
        get() = binding.editAmountItem

    // State:

    // Part of our (ugly) hack to allow SATs as an input currency option
    @State
    @JvmField
    var satSelectedAsCurrency = false

    @State
    @JvmField
    var highFees = false

    private var countdownTimer: MuunCountdownTimer? = null

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        super.initializeUi(view)

        binding.addressTypeItem.setOnAddressTypeChangedListener(this)
        binding.unifiedQrSettings.setOnClickListener { presenter.toggleAdvancedSettings() }
        binding.createOtherInvoice.setOnClickListener { onCreateInvoiceClick() }

        binding.unifiedQrSettings.setExpanded(false)

        if (OS.supportsNotificationRuntimePermission()) {
            binding.unifiedQrNotificationsPriming.setUpForLightning()
            binding.unifiedQrNotificationsPriming.setEnableClickListener {
                presenter.handleNotificationPermissionPrompt()
            }
        }
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            visibility = View.VISIBLE
            showTitle(R.string.show_unified_qr_title)
        }
    }

    override fun setShowHighFeesWarning() {
        highFees = true
    }

    override fun setLoading(loading: Boolean) {
        binding.unifiedQrLoading.visibility = if (loading) View.VISIBLE else View.GONE
        qrContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE // Keep view Bounds

        copyButton.isEnabled = !loading
        shareButton.isEnabled = !loading

        editAmountItem.setLoading(loading)
        binding.expirationTimeItem.setLoading(loading)
        binding.addressTypeItem.isClickable = !loading
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

        binding.addressTypeItem.show(addressType)

        stopTimer()
        countdownTimer = MuunCountdownTimer(bitcoinUri.invoice.remainingMillis(), this)

        countdownTimer!!.start()

        if (amount != null) {
            editAmountItem.setAmount(amount, getBitcoinUnit())
        } else {
            editAmountItem.resetAmount()
        }
    }

    override fun setTaprootState(status: UserActivatedFeatureStatus) {
        binding.addressTypeItem.taprootStatus = status
    }

    override fun onStop() {
        super.onStop()
        stopTimer()
    }

    override fun resetAmount() {
        presenter.setAmount(null)
    }

    override fun showFullContent(bitcoinUri: String, address: String, invoice: String) {
        val dialog = UnifiedQrFullContentDialogFragment()

        dialog.setAddress(address)
        dialog.setInvoice(invoice)

        dialog.setOnAddressClickListener { presenter.copyAddress() }
        dialog.setOnInvoiceClickListener { presenter.copyInvoice() }

        showDrawerDialog(dialog)
    }

    override fun toggleAdvancedSettings() {
        binding.unifiedQrSettings.toggleSection()

        if (binding.unifiedQrSettingsContent.visibility == View.VISIBLE) {
            binding.unifiedQrSettingsContent.visibility = View.GONE
        } else {
            binding.unifiedQrSettingsContent.visibility = View.VISIBLE

            binding.unifiedQrScrollView.postDelayed({
                binding.unifiedQrScrollView.fullScroll(View.FOCUS_DOWN)
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

    override fun onCountDownTick(remainingSeconds: Long) {
        // Using minimalistic pattern/display/formatting to better fit in small screen space
        binding.expirationTimeItem.setExpirationTime(
            ReceiveLnInvoiceFormatter().formatSeconds(remainingSeconds)
        )
    }

    override fun onCountDownFinish() {
        showInvoiceExpiredOverlay()
    }

    private fun showHighFeesOverlay() {
        binding.highFeesWarningOverlay.visibility = View.VISIBLE
        binding.unifiedQrInvoiceExpiredOverlay.visibility = View.GONE
        binding.qrOverlay.visibility = View.GONE
        binding.unifiedQrSettings.visibility = View.GONE
        binding.unifiedQrSettingsContent.visibility = View.GONE
    }

    private fun showInvoiceExpiredOverlay() {
        binding.unifiedQrInvoiceExpiredOverlay.visibility = View.VISIBLE
        binding.qrOverlay.visibility = View.GONE
        binding.unifiedQrSettings.visibility = View.GONE
        binding.unifiedQrSettingsContent.visibility = View.GONE
    }

    private fun onCreateInvoiceClick() {
        presenter.generateNewEmptyUri()
        resetViewState()
    }

    private fun resetViewState() {
        binding.unifiedQrNotificationsPriming.visibility = View.GONE
        binding.highFeesWarningOverlay.visibility = View.GONE
        binding.unifiedQrInvoiceExpiredOverlay.visibility = View.GONE
        binding.qrOverlay.visibility = View.VISIBLE
        binding.unifiedQrSettings.visibility = View.VISIBLE

        if (showNotificationPriming()) {
            binding.unifiedQrNotificationsPriming.visibility = View.VISIBLE

            binding.highFeesWarningOverlay.visibility = View.GONE
            binding.unifiedQrInvoiceExpiredOverlay.visibility = View.GONE
            binding.qrOverlay.visibility = View.GONE
            binding.unifiedQrSettings.visibility = View.GONE
        }
    }

    private fun showNotificationPriming() =
        OS.supportsNotificationRuntimePermission() && !parentActivity.hasNotificationsPermission()

    private fun stopTimer() {
        if (countdownTimer != null) {
            countdownTimer!!.cancel()
            countdownTimer = null
        }
    }

    override fun refresh() {
        if (highFees) {
            showHighFeesOverlay()
        } else {
            resetViewState()
            presenter.generateNewUri()
        }
    }

    // Part of our (ugly) hack to allow SATs as an input currency option
    private fun getBitcoinUnit() = if (satSelectedAsCurrency) {
        BitcoinUnit.SATS
    } else {
        BitcoinUnit.BTC
    }
}
