package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CallSuper
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import io.muun.apollo.R
import io.muun.apollo.domain.analytics.AnalyticsEvent.ADDRESS_ORIGIN
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.isInNightMode
import io.muun.apollo.presentation.ui.view.EditAmountItem
import io.muun.apollo.presentation.ui.view.MuunButton
import timber.log.Timber


abstract class QrFragment<PresenterT : QrPresenter<*>> : SingleFragment<PresenterT>(), QrView {

    protected abstract val qrImage: ImageView
    protected abstract val qrContent: TextView
    protected abstract val copyButton: MuunButton
    protected abstract val shareButton: MuunButton
    protected abstract val editAmountItem: EditAmountItem

    private val qrCodeSize: Int
        get() = resources.getDimensionPixelSize(R.dimen.qr_code_size)

    @CallSuper
    override fun initializeUi(view: View) {
        qrContent.setOnClickListener { presenter.showFullContent() }
        qrImage.setOnClickListener { presenter.copyQrContent(ADDRESS_ORIGIN.QR) }
        copyButton.setOnClickListener { presenter.copyQrContent(ADDRESS_ORIGIN.COPY_BUTTON) }
        shareButton.setOnClickListener { presenter.shareQrContent() }

        editAmountItem.setEditAmountHandler(presenter)
    }

    /**
     * Allow children to specify error correction (e.g if content already has error
     * correction/checksum).
     */
    abstract fun getErrorCorrection(): ErrorCorrectionLevel

    override fun setQrContent(displayContent: String, qrContent: String) {

        setShowingText(displayContent)

        try {
            qrImage.setImageBitmap(createQrCode(qrContent, qrCodeSize))

        } catch (error: WriterException) {
            presenter.handleError(error)
        }
    }

    protected fun setShowingText(content: String) {
        qrContent.text = content
    }

    @Throws(WriterException::class)
    fun createQrCode(content: String, size: Int): Bitmap {

        val qrCode = Encoder.encode(content, getErrorCorrection(), mapOf<EncodeHintType, Any>())
        val qrMatrix = qrCode.matrix

        // If on Dark Mode let's add a little white padding for better/nice UI
        val padding = if (isInNightMode()) 1 else 0
        val width = qrMatrix.width + 2 * padding
        val height = qrMatrix.height + 2 * padding

        // Init Matrix with WHITE values
        val pixels = IntArray(width * height) { Color.WHITE }

        for (y in 0 until qrMatrix.height) {
            for (x in 0 until qrMatrix.width) {
                val on = qrMatrix.get(x, y) == 1.toByte()
                pixels[(x + padding) + width * (y + padding)] = if (on) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        // Since we just created a bitmap with default (aka bilinear) filtering, that smooths the
        // contrast between adjacent pixels, which is normally GREAT for images, but no so much for
        // our current case: QRs. So we create another bitmap, using "nearest-neighbor scaling".
        // See: https://www.geeksforgeeks.org/css-image-rendering-property/
        return Bitmap.createScaledBitmap(bitmap, size, size, false)
    }

    private fun screenWidthInPixels(ctx: Context): Int {
        val size = Point()
        (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(size)
        return size.x
    }

    override fun onDestroyView() {
        if (qrImage.drawable != null) {
            Timber.d("Recycling QR image Bitmap")
            (qrImage.drawable as BitmapDrawable).bitmap.recycle()
        }
        super.onDestroyView()
    }
}
