package io.muun.apollo.presentation.ui.new_operation

import newop.Newop

enum class LibwalletNewOpError(private val libwalletSerialization: String) {

    UNPAYABLE(Newop.OperationErrorUnpayable),
    AMOUNT_GREATER_THAN_BALANCE(Newop.OperationErrorAmountGreaterThanBalance),
    AMOUNT_TOO_SMALL(Newop.OperationErrorAmountTooSmall),
    INVALID_ADDRESS(Newop.OperationErrorInvalidAddress),
    INVOICE_EXPIRED(Newop.OperationErrorInvoiceExpired);

    override fun toString() =
        libwalletSerialization
}