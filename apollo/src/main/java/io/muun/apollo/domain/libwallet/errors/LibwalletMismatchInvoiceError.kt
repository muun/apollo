package io.muun.apollo.domain.libwallet.errors

class LibwalletMismatchInvoiceError(field: String, javaValue: Any, goValue: Any) :
    LibwalletMismatchError("invoice", field, javaValue, goValue)