package io.muun.apollo.presentation.ui.show_qr.ln

import android.view.View
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.InvoiceExpirationCountdownTimer
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.LoadingView
import io.muun.common.utils.LnInvoice


class LnInvoiceQrFragment : QrFragment<LnInvoiceQrPresenter>(), LnInvoiceView {

    companion object {
        private const val EXPIRATION_MESSAGE_THRESHOLD_IN_SECONDS: Int = 10 * 60
    }

    @BindView(R.id.invoice_loading)
    lateinit var loadingView: LoadingView

    @BindView(R.id.qr_overlay)
    lateinit var qrOverlay: View

    @BindView(R.id.ln_invoice_expiration_message)
    lateinit var expirationTime: TextView

    @BindView(R.id.invoice_expired_overlay)
    lateinit var invoiceExpiredOverlay: View

    private var countdownTimer: InvoiceExpirationCountdownTimer? = null

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.fragment_show_qr_ln

    override fun setLoading(loading: Boolean) {
        loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        qrOverlay.visibility = if (loading) View.INVISIBLE else View.VISIBLE // Keep cardView bounds
    }

    override fun setInvoice(invoice: LnInvoice) {
        super.setQrContent(invoice.original)

        invoiceExpiredOverlay.visibility = View.GONE
        qrOverlay.visibility = View.VISIBLE
        expirationTime.visibility = View.GONE

        // Detect if 1h left of expiration time, and show countdown
        val expirationTimeInMillis = invoice.expirationTime.toEpochSecond() * 1000
        val remainingMillis = expirationTimeInMillis - System.currentTimeMillis()

        stopTimer()
        countdownTimer = buildCountDownTimer(remainingMillis)
        countdownTimer!!.start()

        expirationTime.setOnClickListener {
            presenter.generateNewInvoice()
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

    override fun preProcessQrContent(content: String): String =
        content.toUpperCase()

    override fun getErrorCorrection(): ErrorCorrectionLevel =
        ErrorCorrectionLevel.L  // Bech 32 already has its own error correction.

    @OnClick(R.id.create_other_invoice)
    fun onCreateInvoiceClick() {
        presenter.generateNewInvoice()
    }

    private fun stopTimer() {
        if (countdownTimer != null) {
            countdownTimer!!.cancel()
            countdownTimer = null
        }
    }

    private fun buildCountDownTimer(remainingMillis: Long): InvoiceExpirationCountdownTimer {

        return object : InvoiceExpirationCountdownTimer(context, remainingMillis) {
            override fun onTextUpdate(remainingSeconds: Long, text: CharSequence) {

                if (remainingSeconds <= Companion.EXPIRATION_MESSAGE_THRESHOLD_IN_SECONDS) {
                    expirationTime.text = StyledStringRes(ctx, R.string.show_qr_invoice_expiration)
                        .toCharSequence(text.toString())
                    expirationTime.visibility = View.VISIBLE
                }
            }

            override fun onFinish() {
                expirationTime.visibility = View.GONE
                invoiceExpiredOverlay.visibility = View.VISIBLE
            }
        }
    }

    fun refresh() {
        presenter.generateNewInvoice()
    }
}
