package io.muun.apollo.presentation.ui.show_qr.bitcoin

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.base.BaseView
import javax.money.MonetaryAmount

interface BitcoinAddressView : BaseView {

    fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean)

    fun setCurrencyDisplayMode(mode: CurrencyDisplayMode)

    fun setContent(content: String, addressType: AddressType, amount: MonetaryAmount?)

    fun showFullAddress(address: String, addressType: AddressType)
}
