package io.muun.apollo.presentation.ui.show_qr.bitcoin

import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.base.BaseView
import javax.money.MonetaryAmount

interface BitcoinAddressView : BaseView {

    fun setShowingAdvancedSettings(showingAdvancedSettings: Boolean)

    fun setContent(content: String, addressType: AddressType, amount: MonetaryAmount?)

    fun setTaprootState(blocksToTaproot: Int, status: UserActivatedFeatureStatus)

    fun showFullAddress(address: String, addressType: AddressType)
}
