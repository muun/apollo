package io.muun.apollo.presentation.ui.show_qr.bitcoin

import io.muun.apollo.presentation.ui.base.BaseView

interface BitcoinAddressView : BaseView {

    fun setAddress(address: String, addressFormat: AddressFormat)

    fun showFullContent(address: String, addressFormat: AddressFormat)
}
