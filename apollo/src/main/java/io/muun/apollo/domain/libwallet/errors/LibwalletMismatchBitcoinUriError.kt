package io.muun.apollo.domain.libwallet.errors

class LibwalletMismatchBitcoinUriError(field: String, javaValue: Any?, goValue: Any?) :
    LibwalletMismatchError("BitcoinUri", field, javaValue, goValue)
