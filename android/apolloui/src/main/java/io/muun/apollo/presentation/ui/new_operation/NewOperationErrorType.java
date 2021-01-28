package io.muun.apollo.presentation.ui.new_operation;

public enum NewOperationErrorType {
    AMOUNT_TOO_SMALL,
    INSUFFICIENT_FUNDS,
    INVOICE_UNREACHABLE_NODE,
    INVOICE_NO_ROUTE,
    INVOICE_EXPIRED,
    INVOICE_WILL_EXPIRE_SOON,
    INVOICE_ALREADY_USED,
    INVOICE_MISSING_AMOUNT,
    INVALID_INVOICE,
    EXCHANGE_RATE_WINDOW_TOO_OLD,
    INVALID_SWAP, // esoteric error. Swapper server response failed validation
    CYCLICAL_SWAP,
    GENERIC
}
