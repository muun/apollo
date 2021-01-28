package io.muun.apollo.presentation.ui.show_qr.bitcoin.help

import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.MuunHeader

class BitcoinAddressHelpFragment : SingleFragment<BitcoinAddressHelpPresenter>() {

    @BindView(R.id.text)
    lateinit var textView: TextView

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_bitcoin_address_help

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        parentActivity.header.let {
            it.attachToActivity(parentActivity)
            it.setNavigation(MuunHeader.Navigation.EXIT)
            it.hideTitle()
            it.setIndicatorText(null)
            it.setElevated(false)
        }

        textView.text = StyledStringRes(requireContext(), R.string.bitcoin_address_help_content)
            .toCharSequence()
    }
}
