package io.muun.apollo.domain.model

import io.muun.common.crypto.hd.MuunAddress

class MuunAddressGroup(
    val legacy: MuunAddress,
    val segwit: MuunAddress,
    val taproot: MuunAddress,
) {

    fun toAddressGroup() =
        AddressGroup(legacy.address, segwit.address, taproot.address)
}

class AddressGroup(
    val legacy: String,
    val segwit: String,
    val taproot: String,
) {

    fun getAddress(addressType: AddressType) =
        when (addressType) {
            AddressType.LEGACY -> legacy
            AddressType.SEGWIT -> segwit
            AddressType.TAPROOT -> taproot
        }
}