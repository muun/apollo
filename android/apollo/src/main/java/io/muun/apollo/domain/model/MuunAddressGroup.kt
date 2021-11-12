package io.muun.apollo.domain.model

import io.muun.common.crypto.hd.MuunAddress

class MuunAddressGroup(
    val legacy: MuunAddress,
    val segwit: MuunAddress,
    val taproot: MuunAddress
)