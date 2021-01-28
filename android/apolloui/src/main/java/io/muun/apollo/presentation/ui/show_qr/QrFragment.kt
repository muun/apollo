package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import butterknife.BindDimen
import butterknife.BindView
import butterknife.OnClick
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.muun.apollo.R
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.UiUtils
import java.util.*


abstract class QrFragment<PresenterT : QrPresenter<*>> : SingleFragment<PresenterT>(), QrView {

    @BindView(R.id.show_qr_image_qr)
    lateinit var qrImage: ImageView

    @BindView(R.id.show_qr_content)
    lateinit var qrContent: TextView

    @BindDimen(R.dimen.qr_code_size)
    @JvmField
    internal var qrCodeSize: Int = 0

    private val PREVIEW_AFFIX_LENGTH = 15 // This way ellipsized text will always fit in 1 line

    /**
     * Allow children to enable/disable extra QR compression mode if content is Upper alphanumeric.
     */
    abstract fun preProcessQrContent(content: String): String

    /**
     * Allow children to specify error correction (e.g if content already has error
     * correction/checksum).
     */
    abstract fun getErrorCorrection(): ErrorCorrectionLevel

    override fun setQrContent(content: String) {

        qrContent.text = content

        if (!qrContentFitsInOneLine()) {
            qrContent.text = UiUtils.ellipsize(content, PREVIEW_AFFIX_LENGTH)
        }

        try {
            qrImage.setImageBitmap(createQrCode(preProcessQrContent(content), qrCodeSize))

        } catch (error: WriterException) {
            presenter.handleError(error)
        }
    }

    protected fun adjust() {
        // Un-elegant workaround to fit complex layout in all screens (small ones are a pain!)
        // Both constants (800 and 1.25) were arbitrarily chosen after thorough research
        if (resources.configuration.screenHeightDp <= 800) {
            (qrImage.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "1.25"
        }
    }

    @Throws(WriterException::class)
    fun createQrCode(content: String, size: Int): Bitmap {

        val writer = QRCodeWriter()

        val hints = Hashtable<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        hints[EncodeHintType.ERROR_CORRECTION] = getErrorCorrection()

        val qrMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val width = qrMatrix.width
        val height = qrMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[x + width * y] = if (qrMatrix.get(x, y)) Color.BLACK else Color.TRANSPARENT
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return bitmap
    }

    /**
     * Handle click on QR code.
     */
    @OnClick(R.id.show_qr_image_qr)
    fun onQrCodeClick() {
        if (presenter.hasLoadedCorrectly()) {
            presenter.copyQrContent(AnalyticsEvent.ADDRESS_ORIGIN.QR)
        }
    }

    /**
     * Handle click on QR content.
     */
    @OnClick(R.id.show_qr_content)
    fun onQrContentClick() {
        if (presenter.hasLoadedCorrectly()) {
            presenter.showFullContent()
        }
    }

    /**
     * Handle the SHARE button click.
     */
    @OnClick(R.id.show_qr_share)
    fun onShareClick() {
        if (presenter.hasLoadedCorrectly()) {
            presenter.shareQrContent()
        }
    }

    /**
     * Handle the COPY button click.
     */
    @OnClick(R.id.show_qr_copy)
    fun onCopyClick() {
        if (presenter.hasLoadedCorrectly()) {
            presenter.copyQrContent(AnalyticsEvent.ADDRESS_ORIGIN.COPY_BUTTON)
        }
    }

    /**
     * We ask the view to measure itself (without drawing to avoid visual glitches), and for
     * textView that means that it calculates the lineCount.
     */
    private fun qrContentFitsInOneLine(): Boolean {
        val context = requireContext()

        // To calculate the width where textView must fit, we take the screen width and substract
        // view's ancestors margins and padding, also need to take into account compound drawable.
        // So, 44dp = 64dp (ancestors margin sum) - 10dp (drawable padding) - 10dp (drawable size)
        val parentWidth = screenWidthInPixels(context) - UiUtils.dpToPx(context, 44)

        qrContent.measure(
            View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))

        return qrContent.lineCount == 1
    }

    private fun screenWidthInPixels(ctx: Context): Int {
        val size = Point()
        (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(size)
        return size.x
    }
}