package io.muun.apollo.presentation.ui.show_qr.ln

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
import io.muun.apollo.databinding.FragmentShowQrLnBinding
import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.MuunCountdownTimer
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.select_amount.SelectAmountActivity
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.utils.ReceiveLnInvoiceFormatter
import io.muun.apollo.presentation.ui.view.EditAmountItem
import io.muun.apollo.presentation.ui.view.MuunButton
import java.util.Locale
import javax.money.MonetaryAmount

class LnInvoiceQrFragment : QrFragment<LnInvoiceQrPresenter>(),
    LnInvoiceView,
    MuunCountdownTimer.CountDownTimerListener {

    companion object {
        private const val REQUEST_AMOUNT = 2
    }

    private val binding: FragmentShowQrLnBinding
        get() = getBinding() as FragmentShowQrLnBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        FragmentShowQrLnBinding::inflate

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

        binding.invoiceSettings.setOnClickListener { presenter.toggleAdvancedSettings() }
        binding.createOtherInvoice.setOnClickListener { onCreateInvoiceClick() }

        if (OS.supportsNotificationRuntimePermission()) {
            binding.invoiceNotificationsPriming.setUpForLightning()
            binding.invoiceNotificationsPriming.setEnableClickListener {
                presenter.handleNotificationPermissionPrompt()
            }
        }
    }

    override fun setShowHighFeesWarning() {
        highFees = true
    }

    override fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean) {
        binding.invoiceSettings.setExpanded(showingAdvancedSettings)
        if (showingAdvancedSettings) {
            binding.invoiceSettingsContent.visibility = View.VISIBLE
        }
    }

    override fun setLoading(loading: Boolean) {
        binding.invoiceLoading.visibility = if (loading) View.VISIBLE else View.GONE
        qrContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE // Keep view Bounds

        copyButton.isEnabled = !loading
        shareButton.isEnabled = !loading

        editAmountItem.setLoading(loading)
        binding.expirationTimeItem.setLoading(loading)
    }

    override fun setInvoice(invoice: DecodedInvoice, amount: MonetaryAmount?) {

        // Enable extra QR compression mode. Uppercase bech32 strings are more efficiently encoded
        super.setQrContent(invoice.original, invoice.original.uppercase(Locale.getDefault()))

        stopTimer()
        countdownTimer = MuunCountdownTimer(invoice.remainingMillis(), this)
        countdownTimer!!.start()

        if (amount != null) {
            editAmountItem.setAmount(amount, getBitcoinUnit())
        } else {
            editAmountItem.resetAmount()
        }
    }

    override fun onStop() {
        super.onStop()
        stopTimer()
    }

    override fun showFullContent(invoice: String) {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.your_ln_invoice)
        dialog.setDescription(invoice)
        showDrawerDialog(dialog)
    }

    override fun getErrorCorrection(): ErrorCorrectionLevel =
        ErrorCorrectionLevel.L  // Bech 32 already has its own error correction.

    override fun toggleAdvancedSettings() {
        binding.invoiceSettings.toggleSection()

        if (binding.invoiceSettingsContent.visibility == View.VISIBLE) {
            binding.invoiceSettingsContent.visibility = View.GONE
        } else {
            binding.invoiceSettingsContent.visibility = View.VISIBLE

            binding.scrollView.postDelayed({
                binding.scrollView.fullScroll(View.FOCUS_DOWN)
            }, 100)
        }
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

    override fun resetAmount() {
        presenter.setAmount(null)
    }

    override fun onCountDownTick(remainingSeconds: Long) {
        // Using minimalistic pattern/display/formatting to better fit in small screen space
        binding.expirationTimeItem.setExpirationTime(
            ReceiveLnInvoiceFormatter().formatSeconds(remainingSeconds)
        )
    }

    override fun onCountDownFinish() {
        showInvoiceExpiredOverlay()
    }

    private fun resetViewState() {
        binding.invoiceNotificationsPriming.visibility = View.GONE
        binding.highFeesWarningOverlay.visibility = View.GONE
        binding.invoiceExpiredOverlay.visibility = View.GONE
        binding.qrOverlay.visibility = View.VISIBLE
        binding.invoiceSettings.visibility = View.VISIBLE

        if (showNotificationPriming()) {
            binding.invoiceNotificationsPriming.visibility = View.VISIBLE

            binding.highFeesWarningOverlay.visibility = View.GONE
            binding.invoiceExpiredOverlay.visibility = View.GONE
            binding.qrOverlay.visibility = View.GONE
            binding.invoiceSettings.visibility = View.GONE
            binding.invoiceSettingsContent.visibility = View.GONE
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

    private fun showHighFeesOverlay() {
        binding.highFeesWarningOverlay.visibility = View.VISIBLE
        binding.invoiceExpiredOverlay.visibility = View.GONE
        binding.qrOverlay.visibility = View.GONE
        binding.invoiceSettings.visibility = View.GONE
        binding.invoiceSettings.setExpanded(false)
        binding.invoiceSettingsContent.visibility = View.GONE
    }

    private fun showInvoiceExpiredOverlay() {
        binding.invoiceExpiredOverlay.visibility = View.VISIBLE
        binding.qrOverlay.visibility = View.GONE
        binding.invoiceSettings.visibility = View.GONE
        binding.invoiceSettings.setExpanded(false)
        binding.invoiceSettingsContent.visibility = View.GONE
    }

    private fun onCreateInvoiceClick() {
        presenter.generateNewEmptyInvoice()
        resetViewState()
    }

    override fun refresh() {
        if (highFees) {
            showHighFeesOverlay()
        } else {
            resetViewState()
            presenter.generateNewInvoice()
        }
    }

    // Part of our (ugly) hack to allow SATs as an input currency option
    private fun getBitcoinUnit() = if (satSelectedAsCurrency) {
        BitcoinUnit.SATS
    } else {
        BitcoinUnit.BTC
    }
}
