package io.muun.apollo.presentation.ui.show_qr.bitcoin

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import butterknife.BindDrawable
import butterknife.BindView
import butterknife.OnClick
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.show_qr.QrFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.UiUtils

class BitcoinAddressQrFragment : QrFragment<BitcoinAddressQrPresenter>(), BitcoinAddressView {

    @BindView(R.id.legacy_address_info)
    lateinit var legacyAddressInfo: TextView

    @BindView(R.id.switch_bitcoin_address_format)
    lateinit var switchAddressFormat: TextView

    @BindDrawable(R.drawable.ic_visibility)
    lateinit var visibilityIcon: Drawable

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_show_qr

    override fun setAddress(address: String, addressFormat: AddressFormat) {
        super.setQrContent(address)
        super.adjust()

        val switchFormatStringRes = when (addressFormat) {
            AddressFormat.SEGWIT -> R.string.show_qr_switch_to_legacy_format
            AddressFormat.LEGACY -> R.string.show_qr_switch_to_segwit_format
        }

        // Using embedded link style but setting onClick to entire textView (easier to click)
        switchAddressFormat.text = StyledStringRes(requireContext(), switchFormatStringRes)
            .toCharSequence()

        legacyAddressInfo.visibility = if (addressFormat == AddressFormat.LEGACY) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (addressFormat == AddressFormat.LEGACY) {
            qrContent.setCompoundDrawables(null, null, null, null)
            qrContent.compoundDrawablePadding = 0

        } else {
            qrContent.setCompoundDrawablesWithIntrinsicBounds(null, null, visibilityIcon, null)
            qrContent.compoundDrawablePadding = UiUtils.dpToPx(context, 10)
        }

    }

    override fun showFullContent(address: String, addressFormat: AddressFormat) {

        val title = when (addressFormat) {
            AddressFormat.SEGWIT -> R.string.your_bitcoin_address
            AddressFormat.LEGACY -> R.string.your_compat_bitcoin_address
        }

        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(title)
        dialog.setDescription(address)
        showDrawerDialog(dialog)
    }

    override fun preProcessQrContent(content: String): String =
        content // No pre-processing

    override fun getErrorCorrection(): ErrorCorrectionLevel =
        ErrorCorrectionLevel.H

    @OnClick(R.id.switch_bitcoin_address_format)
    fun onSwitchAddressFormatClick() {
        presenter.switchAddressFormat()
    }

    @OnClick(R.id.legacy_address_info)
    fun onLegacyAddressInfoClick() {
        presenter.showHelp()
    }
}