package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.model.AddressGroup
import io.muun.apollo.domain.model.AddressType
import io.muun.apollo.domain.model.BitcoinAmount

class DecodedBitcoinUri(
    val addressGroup: AddressGroup,
    val invoice: DecodedInvoice,
    val amount: BitcoinAmount?,
) {

    fun getUriFor(addressType: AddressType): String {
        val address = addressGroup.getAddress(addressType)
        return BitcoinUri.generate(address, invoice.original, amount, invoice.networkParameters)
    }
}