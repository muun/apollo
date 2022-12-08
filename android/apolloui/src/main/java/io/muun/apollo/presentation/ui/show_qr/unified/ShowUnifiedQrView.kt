package io.muun.apollo.presentation.ui.show_qr.unified

import io.muun.apollo.domain.libwallet.DecodedBitcoinUri
import io.muun.apollo.presentation.ui.show_qr.QrView
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import javax.money.MonetaryAmount

interface ShowUnifiedQrView : QrView {

    fun setLoading(loading: Boolean)

    fun setBitcoinUri(
        bitcoinUri: DecodedBitcoinUri,
        addressType: AddressType,
        amount: MonetaryAmount?,
    )

    fun resetAmount()

    fun showFullContent(bitcoinUri: String, address: String, invoice: String)

    fun setTaprootState(status: UserActivatedFeatureStatus)

}
