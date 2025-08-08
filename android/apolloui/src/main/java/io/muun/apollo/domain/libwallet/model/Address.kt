package io.muun.apollo.domain.libwallet.model

import io.muun.common.crypto.hd.MuunAddress

class Address(val address: MuunAddress) : libwallet.MuunAddress {

    override fun derivationPath(): String =
        address.derivationPath

    override fun version(): Long =
        address.version.toLong()

    override fun address(): String =
        address.address
}